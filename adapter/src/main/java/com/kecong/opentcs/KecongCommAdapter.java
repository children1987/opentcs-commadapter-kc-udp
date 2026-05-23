package com.kecong.opentcs;

import com.kecong.opentcs.protocol.*;
import com.kecong.opentcs.protocol.model.NavigationTask;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskAction;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPath;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPoint;
import com.kecong.opentcs.protocol.model.RobotStatus;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Triple;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.Route;
import org.opentcs.drivers.vehicle.MovementCommand;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleProcessModel;
import org.opentcs.util.ExplainedBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * openTCS VehicleCommAdapter implementation for Kecong (科聪) AGV controllers.
 *
 * <p>Implements the openTCS driver interface using the Kecong UDP protocol V2.0.
 * Supports hybrid navigation (laser + QR code) via the 0xAE/0xAF/0xB1/0xB2 commands.
 *
 * <h3>Interaction Flow</h3>
 * <ol>
 *   <li>enable() → subscribe to vehicle status → poll status → init auto mode → ready</li>
 *   <li>sendMovementCommand() → translate openTCS route to 0xAE navigation task → wait for completion</li>
 *   <li>disable() → unsubscribe → close UDP channel</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * The driver reads these properties from the vehicle properties or system properties:
 * <ul>
 *   <li>{@code kecong:host} — controller IP address (default: 192.168.100.178)</li>
 *   <li>{@code kecong:port} — navigation UDP port (default: 17804)</li>
 *   <li>{@code kecong:varPort} — variable/QR/magnetic UDP port (default: 17800)</li>
 *   <li>{@code kecong:authCode} — protocol auth code (required, contact Kecong sales)</li>
 *   <li>{@code kecong:pollInterval} — status poll interval in ms (default: 100)</li>
 * </ul>
 */
public class KecongCommAdapter implements VehicleCommAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(KecongCommAdapter.class);

    /** Default controller IP */
    private static final String DEFAULT_HOST = "192.168.100.178";
    /** Default navigation port */
    private static final int DEFAULT_NAV_PORT = 17804;
    /** Default variable/QR/magnetic port */
    private static final int DEFAULT_VAR_PORT = 17800;
    /** Default status polling interval (ms) */
    private static final int DEFAULT_POLL_INTERVAL = 100;
    /** Subscription duration (ms) — 60 seconds, refreshed periodically */
    private static final int SUBSCRIPTION_DURATION_MS = 60_000;
    /** Subscription refresh interval (ms) */
    private static final long SUBSCRIPTION_REFRESH_MS = 30_000;
    /** Max pending commands */
    private static final int MAX_COMMAND_QUEUE_CAPACITY = 1;

    private final KecongVehicleProcessModel processModel;
    private final String controllerHost;
    private final String varHost;
    private final int navPort;
    private final int varPort;
    private final byte[] authCode;
    private final int pollIntervalMs;

    private KecongUdpChannel navChannel;
    private KecongUdpChannel varChannel;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture;
    private ScheduledFuture<?> subscriptionFuture;
    private volatile boolean initialized;
    private volatile boolean enabled;
    private String subscriptionUuid;

    // Task state
    private int currentOrderId = 0;
    private int currentTaskKey = 0;
    private long lastStatusUpdateTime = 0;

    public KecongCommAdapter(KecongVehicleProcessModel processModel,
                             String controllerHost,
                             int navPort,
                             int varPort,
                             String varHost,
                             String authCodeStr,
                             int pollIntervalMs) {
        this.processModel = Objects.requireNonNull(processModel, "processModel");
        this.controllerHost = controllerHost != null ? controllerHost : DEFAULT_HOST;
        this.varHost = varHost != null ? varHost : DEFAULT_HOST;
        this.navPort = navPort > 0 ? navPort : DEFAULT_NAV_PORT;
        this.varPort = varPort > 0 ? varPort : DEFAULT_VAR_PORT;
        this.authCode = authCodeStr != null
                ? Arrays.copyOf(authCodeStr.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 16)
                : new byte[16];
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : DEFAULT_POLL_INTERVAL;
        this.initialized = false;
        this.enabled = false;
    }

    /**
     * Convenience constructor with defaults.
     */
    public KecongCommAdapter(KecongVehicleProcessModel processModel, String authCodeStr) {
        this(processModel, DEFAULT_HOST, DEFAULT_NAV_PORT, DEFAULT_VAR_PORT, DEFAULT_HOST, authCodeStr, DEFAULT_POLL_INTERVAL);
    }

    // ===== VehicleCommAdapter interface =====

    @Override
    public void initialize() {
        if (initialized) return;

        LOG.info("Initializing KecongCommAdapter: host={}, navPort={}, varHost={}, varPort={}, pollInterval={}ms",
                controllerHost, navPort, varHost, varPort, pollIntervalMs);

        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "kecong-poller");
            t.setDaemon(true);
            return t;
        });

        this.subscriptionUuid = UUID.randomUUID().toString();
        initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public synchronized void enable() {
        if (!initialized) {
            throw new IllegalStateException("Not initialized");
        }
        if (enabled) return;

        LOG.info("Enabling KecongCommAdapter for vehicle '{}'", getName());

        try {
            // Open both UDP channels
            navChannel = new KecongUdpChannel(controllerHost, navPort, authCode, pollIntervalMs * 2);
            varChannel = new KecongUdpChannel(varHost, varPort, authCode, pollIntervalMs * 2);

            // Start status polling
            pollFuture = scheduler.scheduleAtFixedRate(
                    this::pollRobotStatus,
                    pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);

            // Start subscription refresh
            subscriptionFuture = scheduler.scheduleAtFixedRate(
                    this::refreshSubscription,
                    0, SUBSCRIPTION_REFRESH_MS, TimeUnit.MILLISECONDS);

            enabled = true;
            LOG.info("KecongCommAdapter enabled for '{}'", getName());
        } catch (IOException e) {
            LOG.error("Failed to enable KecongCommAdapter: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to open UDP channel", e);
        }
    }

    @Override
    public synchronized void disable() {
        if (!enabled) return;

        LOG.info("Disabling KecongCommAdapter for '{}'", getName());

        enabled = false;

        if (pollFuture != null) {
            pollFuture.cancel(false);
            pollFuture = null;
        }
        if (subscriptionFuture != null) {
            subscriptionFuture.cancel(false);
            subscriptionFuture = null;
        }
        if (navChannel != null) {
            navChannel.close();
            navChannel = null;
        }
        if (varChannel != null) {
            varChannel.close();
            varChannel = null;
        }

        // Update process model
        processModel.setVehicleState(Vehicle.State.UNKNOWN);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Nonnull
    @Override
    public VehicleProcessModel getProcessModel() {
        return processModel;
    }

    @Override
    public int getCommandQueueCapacity() {
        return MAX_COMMAND_QUEUE_CAPACITY;
    }

    @Override
    public synchronized void sendCommand(@Nonnull MovementCommand cmd) throws IllegalArgumentException {
        if (!enabled) {
            throw new IllegalStateException("Adapter not enabled");
        }
        if (cmd == null) {
            throw new IllegalArgumentException("MovementCommand is null");
        }

        LOG.info("Processing movement command: step={}, dest={}, op={}",
                cmd.getStep().getRouteIndex(),
                cmd.getStep().getDestinationPoint().getName(),
                cmd.getOperation());

        try {
            // Build navigation task from movement command
            NavigationTask task = buildNavigationTask(cmd);
            if (task == null) {
                LOG.warn("Could not build navigation task from command");
                return;
            }

            // Update task state
            currentOrderId = task.getOrderId();
            currentTaskKey = task.getTaskKey();

            // Send navigation task
            byte[] taskData = KecongMessageEncoder.encodeNavigationTask(task);
            boolean success = navChannel.sendAndVerify(KecongCommandCode.CMD_HYBRID_NAV_TASK, taskData);

            if (success) {
                LOG.info("Navigation task dispatched: orderId={}, taskKey={}, {} points",
                        currentOrderId, currentTaskKey, task.getPoints().size());

                // Update process model
                processModel.setVehicleState(Vehicle.State.EXECUTING);
                processModel.getSentCommands().add(cmd);

                // Wait for task completion (with timeout)
                waitForTaskCompletion(cmd);
            } else {
                LOG.error("Failed to dispatch navigation task: orderId={}", currentOrderId);
                processModel.setVehicleState(Vehicle.State.ERROR);
            }
        } catch (IOException e) {
            LOG.error("I/O error dispatching navigation task: {}", e.getMessage(), e);
            processModel.setVehicleState(Vehicle.State.ERROR);
        }
    }

    @Override
    public synchronized boolean canSendNextCommand() {
        return enabled
                && !peekCommandQueue().isEmpty()
                && processModel.getVehicleState() != Vehicle.State.EXECUTING;
    }

    @Nonnull
    @Override
    public ExplainedBoolean canProcess(@Nonnull List<String> operations) {
        Objects.requireNonNull(operations);
        // Kecong supports all standard AGV operations
        return new ExplainedBoolean(true, "Supported");
    }

    @Override
    public void processMessage(@Nonnull Object message) {
        // Not used — Kecong uses polling, not push
    }

    @Override
    public int getSentQueueCapacity() {
        return 1;
    }

    // ===== Robot interaction methods =====

    /**
     * Subscribe to robot status (0xB1) with AGV state and cargo state.
     */
    private void subscribe() {
        if (navChannel == null || navChannel.isClosed()) return;

        try {
            byte[] subData = KecongMessageEncoder.encodeSubscription(
                    new byte[]{KecongCommandCode.CMD_QUERY_ROBOT_STATUS, KecongCommandCode.CMD_QUERY_CARGO_STATUS},
                    pollIntervalMs,
                    SUBSCRIPTION_DURATION_MS,
                    false,  // periodic report
                    subscriptionUuid
            );
            boolean ok = navChannel.sendAndVerify(KecongCommandCode.CMD_SUBSCRIPTION, subData);
            if (ok) {
                LOG.debug("Subscription registered: {}", subscriptionUuid);
            } else {
                LOG.warn("Failed to register subscription: {}", subscriptionUuid);
            }
        } catch (IOException e) {
            LOG.warn("Subscription error: {}", e.getMessage());
        }
    }

    /**
     * Refresh subscription before it expires.
     */
    private void refreshSubscription() {
        if (!enabled || navChannel == null || navChannel.isClosed()) return;
        subscribe();
    }

    /**
     * Perform auto-mode initialization sequence.
     */
    private boolean initAutoMode() {
        if (navChannel == null || navChannel.isClosed()) return false;

        try {
            // 1. Query robot status (0xAF)
            byte[] statusData = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_ROBOT_STATUS, new byte[0]);
            if (statusData == null) {
                LOG.debug("Failed to query robot status during init");
                return false;
            }

            RobotStatus status = KecongMessageDecoder.decodeRobotStatus(statusData);
            if (status == null) return false;

            // 2. Check if already in auto mode
            if (status.isAutoMode() && status.getLocalizationStatus() == 3) {
                LOG.info("Robot already in auto mode with localization complete");
                processModel.setAutoReady(true);
                processModel.setVehicleState(Vehicle.State.IDLE);
                return true;
            }

            // 3. Switch to manual mode → manual position → confirm position → switch to auto
            // Step: NaviControl = 0 (manual)
            byte[] manualModeData = encodeVariableWrite("NaviControl", new byte[]{0, 0, 0, 0});
            varChannel.sendAndVerify(KecongCommandCode.CMD_WRITE_VAR, manualModeData);

            // Step: Manual position (0x14)
            navChannel.sendAndVerify(KecongCommandCode.CMD_MANUAL_POSITION, new byte[0]);

            // Wait briefly
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // Step: Query and wait for localization complete
            for (int i = 0; i < 30; i++) {  // max ~6 seconds
                statusData = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_ROBOT_STATUS, new byte[0]);
                if (statusData != null) {
                    status = KecongMessageDecoder.decodeRobotStatus(statusData);
                    if (status != null && status.getLocalizationStatus() == 3) {
                        break;
                    }
                }
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }

            // Confirm position (0x1F)
            navChannel.sendAndVerify(KecongCommandCode.CMD_CONFIRM_POSITION, new byte[0]);

            // Switch to auto mode: NaviControl = 1
            byte[] autoModeData = encodeVariableWrite("NaviControl", new byte[]{1, 0, 0, 0});
            varChannel.sendAndVerify(KecongCommandCode.CMD_WRITE_VAR, autoModeData);

            LOG.info("Auto mode initialization complete");
            processModel.setAutoReady(true);
            processModel.setVehicleState(Vehicle.State.IDLE);
            return true;
        } catch (IOException | InterruptedException e) {
            LOG.error("Auto mode init error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Poll robot status (0xAF) and update process model.
     */
    private void pollRobotStatus() {
        if (!enabled || navChannel == null || navChannel.isClosed()) return;

        try {
            // Always query 0xAF before any navigation command (protocol requirement)
            byte[] statusData = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_ROBOT_STATUS, new byte[0]);
            if (statusData == null) {
                // On first timeout, try init auto mode
                if (!processModel.isAutoReady()) {
                    initAutoMode();
                }
                return;
            }

            RobotStatus status = KecongMessageDecoder.decodeRobotStatus(statusData);
            if (status == null) return;

            lastStatusUpdateTime = System.currentTimeMillis();

            // Update process model
            processModel.setVehiclePosition(new Triple(
                    (long) (status.getPositionX() * 1000),  // m → mm
                    (long) (status.getPositionY() * 1000),
                    0));
            processModel.setVehicleOrientationAngle(status.getHeadingAngle());
            processModel.setVehicleState(translateAgvState(status));
            processModel.setKecongWorkMode(status.getWorkMode());
            processModel.setKecongAgvState(status.getAgvState());
            processModel.setLocalizationStatus(status.getLocalizationStatus());
            processModel.setConfidence(status.getConfidence());
            processModel.setBatteryPercent(status.getBatteryPercent());
            processModel.setChargeStatus(status.getChargeStatus());
            processModel.setVehicleEnergyLevel((int) (status.getBatteryPercent() * 100));
            processModel.setCmdSequence(navChannel.getSequenceNumber());

            // Handle errors
            if (status.hasError()) {
                String errorCodes = Arrays.stream(status.getAbnormalEvents())
                        .filter(ab -> ab.isError())
                        .map(ab -> String.format("0x%04X", ab.getEventCode()))
                        .collect(Collectors.joining(","));
                processModel.setErrorCodes(errorCodes);
                if (processModel.getVehicleState() != Vehicle.State.EXECUTING) {
                    processModel.setVehicleState(Vehicle.State.ERROR);
                }
                LOG.warn("Robot has errors: {}", errorCodes);
            }

            LOG.trace("Robot status: {}", status);
        } catch (IOException e) {
            LOG.debug("Poll error: {}", e.getMessage());
        }
    }

    /**
     * Build a Kecong navigation task from an openTCS movement command.
     */
    private NavigationTask buildNavigationTask(MovementCommand cmd) {
        Route.Step step = cmd.getStep();
        Point destPoint = step.getDestinationPoint();
        if (destPoint == null) {
            LOG.warn("No destination point in movement command");
            return null;
        }

        // Get the full route for remaining steps
        List<Route.Step> remainingSteps = cmd.getRoute().getSteps().stream()
                .filter(s -> s.getRouteIndex() >= step.getRouteIndex())
                .collect(Collectors.toList());

        // Generate new order/task IDs
        int orderId = currentOrderId + 1;
        int taskKey = 1;

        NavigationTask.Builder taskBuilder = NavigationTask.builder()
                .orderId(orderId)
                .taskKey(taskKey)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE);

        // Convert route steps to path points
        int seqNumber = 0;
        for (Route.Step s : remainingSteps) {
            Point p = s.getDestinationPoint();
            // Assume point name contains numeric ID (configurable via properties)
            int pointId = extractPointId(p);
            TaskPoint tp = TaskPoint.builder()
                    .sequenceNumber(seqNumber)
                    .pointId(pointId)
                    .build();
            taskBuilder.addPoint(tp);
            seqNumber += 2;  // even numbers: 0, 2, 4, ...
        }

        // Add action at destination if operation specified
        String operation = cmd.getOperation();
        if (operation != null && !operation.isEmpty() && !remainingSteps.isEmpty()) {
            TaskPoint lastPoint = taskBuilder.build().getPoints().get(taskBuilder.build().getPoints().size() - 1);
            TaskAction action = createActionForOperation(operation);
            if (action != null) {
                // Rebuild with action
                List<TaskPoint> points = new ArrayList<>(taskBuilder.build().getPoints());
                TaskPoint modified = TaskPoint.builder()
                        .sequenceNumber(lastPoint.getSequenceNumber())
                        .pointId(lastPoint.getPointId())
                        .addAction(action)
                        .build();
                points.set(points.size() - 1, modified);
                taskBuilder.points(points);
            }
        }

        return taskBuilder.build();
    }

    /**
     * Extract numeric point ID from openTCS point.
     * By default, tries to parse the point name as an integer.
     * Override for custom mapping.
     */
    protected int extractPointId(Point point) {
        try {
            return Integer.parseInt(point.getName());
        } catch (NumberFormatException e) {
            // Try properties
            Map<String, String> props = point.getProperties();
            if (props.containsKey("kecong:pointId")) {
                return Integer.parseInt(props.get("kecong:pointId"));
            }
            // Fall back to hash
            return Math.abs(point.getName().hashCode() % 100000);
        }
    }

    /**
     * Create a Kecong action for an openTCS operation string.
     */
    private TaskAction createActionForOperation(String operation) {
        switch (operation.toUpperCase()) {
            case "LOAD":
            case "PICKUP":
                // Pallet lift up (托盘上升)
                return new TaskAction(
                        KecongActionType.ACTION_PALLET_LIFT,
                        KecongActionType.CONCURRENT_SINGLE,
                        (int) (System.currentTimeMillis() % Integer.MAX_VALUE),
                        new byte[]{1, 0, 0, 0});  // 1 = up
            case "UNLOAD":
            case "DROPOFF":
                // Pallet lift down (托盘下降)
                return new TaskAction(
                        KecongActionType.ACTION_PALLET_LIFT,
                        KecongActionType.CONCURRENT_SINGLE,
                        (int) (System.currentTimeMillis() % Integer.MAX_VALUE),
                        new byte[]{2, 0, 0, 0});  // 2 = down
            case "CHARGE":
                // Charge operation (uses variable write)
                return null;  // Handled separately
            default:
                LOG.debug("Unknown operation '{}', skipping action", operation);
                return null;
        }
    }

    /**
     * Translate Kecong AGV state to openTCS Vehicle.State.
     */
    private Vehicle.State translateAgvState(RobotStatus status) {
        if (status.getAgvState() == 0) return Vehicle.State.IDLE;
        if (status.getAgvState() == 1) return Vehicle.State.EXECUTING;
        if (status.getAgvState() == 2) return Vehicle.State.IDLE;  // paused = idle for openTCS
        if (status.getAgvState() == 6) return Vehicle.State.ERROR;
        if (status.getAgvState() == 3) return Vehicle.State.UNAVAILABLE;
        return Vehicle.State.UNKNOWN;
    }

    /**
     * Wait for current navigation task to complete.
     */
    private void waitForTaskCompletion(MovementCommand cmd) {
        int maxWaitMs = 600_000;  // 10 minutes max
        int checkInterval = 500;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < maxWaitMs) {
            if (!enabled) return;

            RobotStatus status = getLatestStatus();
            if (status == null) {
                try { Thread.sleep(checkInterval); } catch (InterruptedException ignored) {}
                continue;
            }

            // Check if task complete
            if (status.getOrderId() == 0 || status.getOrderId() != currentOrderId) {
                // Task finished
                processModel.getSentCommands().remove(cmd);
                processModel.setVehicleState(Vehicle.State.IDLE);
                LOG.info("Navigation task completed: orderId={}", currentOrderId);
                return;
            }

            // Check for errors
            if (status.hasError() || status.isNavFailed()) {
                LOG.error("Navigation failed during task execution");
                processModel.setVehicleState(Vehicle.State.ERROR);
                return;
            }

            try { Thread.sleep(checkInterval); } catch (InterruptedException ignored) {}
        }

        LOG.warn("Task completion wait timeout after {}ms", maxWaitMs);
        processModel.setVehicleState(Vehicle.State.ERROR);
    }

    /**
     * Get latest robot status by polling once.
     */
    private RobotStatus getLatestStatus() {
        try {
            if (navChannel == null || navChannel.isClosed()) return null;
            byte[] data = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_ROBOT_STATUS, new byte[0]);
            if (data == null) return null;
            return KecongMessageDecoder.decodeRobotStatus(data);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Encode a variable write command (0x00).
     */
    private byte[] encodeVariableWrite(String varName, byte[] value) {
        byte[] data = new byte[272];
        byte[] nameBytes = varName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, data, 0, Math.min(nameBytes.length, 16));
        if (value != null) {
            System.arraycopy(value, 0, data, 16, Math.min(value.length, 256));
        }
        return data;
    }

    @Nonnull
    @Override
    public String getName() {
        return processModel.getVehicleName();
    }
}
