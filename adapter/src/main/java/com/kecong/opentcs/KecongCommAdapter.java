package com.kecong.opentcs;

import com.kecong.opentcs.protocol.*;
import com.kecong.opentcs.protocol.model.NavigationTask;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskAction;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPath;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPoint;
import com.kecong.opentcs.protocol.model.RobotStatus;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Pose;
import org.opentcs.data.model.Triple;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.Route;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.drivers.vehicle.MovementCommand;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapterMessage;
import org.opentcs.drivers.vehicle.VehicleProcessModel;
import org.opentcs.drivers.vehicle.management.VehicleProcessModelTO;
import org.opentcs.drivers.vehicle.VehicleCommAdapterDescription;
import org.opentcs.util.ExplainedBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * openTCS VehicleCommAdapter implementation for Kecong AGV controllers (openTCS 7.x).
 */
public class KecongCommAdapter implements VehicleCommAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(KecongCommAdapter.class);

    private static final String DEFAULT_NAV_HOST = "192.168.100.178";
    private static final String DEFAULT_QR_HOST = "192.168.100.200";
    private static final int DEFAULT_NAV_PORT = 17804;
    private static final int DEFAULT_QR_PORT = 17800;
    private static final int DEFAULT_POLL_INTERVAL = 100;
    private static final int SUBSCRIPTION_DURATION_MS = 60_000;
    private static final long SUBSCRIPTION_REFRESH_MS = 30_000;
    private static final int MAX_COMMAND_QUEUE_CAPACITY = 1;

    private final KecongVehicleProcessModel processModel;
    private final String navHost;
    private final String qrHost;
    private final int navPort;
    private final int qrPort;
    private final byte[] authCode;
    private final int pollIntervalMs;

    private KecongUdpChannel navChannel;
    private KecongUdpChannel qrChannel;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture;
    private ScheduledFuture<?> subscriptionFuture;
    private volatile boolean initialized;
    private volatile boolean enabled;
    private String subscriptionUuid;

    // Command queues (managed by adapter in 7.x)
    private final Queue<MovementCommand> sentCommands = new ArrayDeque<>(MAX_COMMAND_QUEUE_CAPACITY);

    // Task state
    private int currentOrderId = 0;
    private int currentTaskKey = 0;
    private long lastStatusUpdateTime = 0;

    public KecongCommAdapter(KecongVehicleProcessModel processModel,
                             String navHost,
                             int navPort,
                             int qrPort,
                             String qrHost,
                             String authCodeStr,
                             int pollIntervalMs) {
        this.processModel = Objects.requireNonNull(processModel, "processModel");
        this.navHost = navHost != null ? navHost : DEFAULT_NAV_HOST;
        this.qrHost = qrHost != null ? qrHost : DEFAULT_QR_HOST;
        this.navPort = navPort > 0 ? navPort : DEFAULT_NAV_PORT;
        this.qrPort = qrPort > 0 ? qrPort : DEFAULT_QR_PORT;
        this.authCode = authCodeStr != null
                ? Arrays.copyOf(authCodeStr.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 16)
                : new byte[16];
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : DEFAULT_POLL_INTERVAL;
        this.initialized = false;
        this.enabled = false;
    }

    public KecongCommAdapter(KecongVehicleProcessModel processModel, String authCodeStr) {
        this(processModel, DEFAULT_NAV_HOST, DEFAULT_NAV_PORT, DEFAULT_QR_PORT, DEFAULT_QR_HOST, authCodeStr, DEFAULT_POLL_INTERVAL);
    }

    // ===== Lifecycle (from VehicleCommAdapter extends Lifecycle) =====

    @Override
    public void initialize() {
        if (initialized) return;
        LOG.info("Initializing KecongCommAdapter: navHost={}, navPort={}, qrHost={}, qrPort={}, pollInterval={}ms",
                navHost, navPort, qrHost, qrPort, pollIntervalMs);
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
    public void terminate() {
        if (!initialized) return;
        disable();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        initialized = false;
    }

    // ===== VehicleCommAdapter interface =====

    @Override
    public synchronized void enable() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        if (enabled) return;
        LOG.info("Enabling KecongCommAdapter for vehicle '{}'", getName());
        try {
            navChannel = new KecongUdpChannel(navHost, navPort, authCode, pollIntervalMs * 2);
            qrChannel = new KecongUdpChannel(qrHost, qrPort, authCode, pollIntervalMs * 2);
            pollFuture = scheduler.scheduleAtFixedRate(
                    this::pollRobotStatus,
                    pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
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
        if (pollFuture != null) { pollFuture.cancel(false); pollFuture = null; }
        if (subscriptionFuture != null) { subscriptionFuture.cancel(false); subscriptionFuture = null; }
        if (navChannel != null) { navChannel.close(); navChannel = null; }
        if (qrChannel != null) { qrChannel.close(); qrChannel = null; }
        sentCommands.clear();
        processModel.setState(Vehicle.State.UNKNOWN);
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Nonnull
    @Override
    public VehicleProcessModel getProcessModel() { return processModel; }

    @Nonnull
    @Override
    public VehicleProcessModelTO createTransferableProcessModel() {
        VehicleProcessModelTO to = new VehicleProcessModelTO();
        to.setName(processModel.getName());
        to.setCommAdapterEnabled(processModel.isCommAdapterEnabled());
        to.setCommAdapterConnected(processModel.isCommAdapterConnected());
        to.setPosition(processModel.getPosition());
        to.setPose(processModel.getPose());
        to.setEnergyLevel(processModel.getEnergyLevel());
        to.setLoadHandlingDevices(processModel.getLoadHandlingDevices());
        to.setState(processModel.getState());
        to.setBoundingBox(processModel.getBoundingBox());
        return to;
    }

    @Override
    public Queue<MovementCommand> getUnsentCommands() { return new ArrayDeque<>(); }

    @Override
    public Queue<MovementCommand> getSentCommands() { return sentCommands; }

    @Override
    public int getCommandsCapacity() { return MAX_COMMAND_QUEUE_CAPACITY; }

    @Override
    public boolean canAcceptNextCommand() {
        return enabled && sentCommands.size() < MAX_COMMAND_QUEUE_CAPACITY;
    }

    @Override
    public String getRechargeOperation() { return "CHARGE"; }

    @Override
    public synchronized boolean enqueueCommand(@Nonnull MovementCommand cmd) throws IllegalArgumentException {
        Objects.requireNonNull(cmd, "cmd");
        if (!enabled) throw new IllegalStateException("Adapter not enabled");
        if (sentCommands.size() >= MAX_COMMAND_QUEUE_CAPACITY) return false;

        Route.Step step = cmd.getStep();
        LOG.info("Processing movement command: stepIdx={}, dest={}, op={}",
                step.getRouteIndex(), step.getDestinationPoint().getName(), cmd.getOperation());

        try {
            NavigationTask task = buildNavigationTask(cmd);
            if (task == null) return false;

            currentOrderId = task.getOrderId();
            currentTaskKey = task.getTaskKey();

            byte[] taskData = KecongMessageEncoder.encodeNavigationTask(task);
            boolean success = navChannel.sendAndVerify(KecongCommandCode.CMD_HYBRID_NAV_TASK, taskData);

            if (success) {
                LOG.info("Navigation task dispatched: orderId={}, taskKey={}, {} points",
                        currentOrderId, currentTaskKey, task.getPoints().size());
                sentCommands.add(cmd);
                processModel.setState(Vehicle.State.EXECUTING);
                waitForTaskCompletion(cmd);
                return true;
            } else {
                LOG.error("Failed to dispatch navigation task: orderId={}", currentOrderId);
                processModel.setState(Vehicle.State.ERROR);
                return false;
            }
        } catch (IOException e) {
            LOG.error("I/O error dispatching navigation task: {}", e.getMessage(), e);
            processModel.setState(Vehicle.State.ERROR);
            return false;
        }
    }

    @Override
    public void clearCommandQueue() {
        sentCommands.clear();
        currentOrderId = 0;
        currentTaskKey = 0;
    }

    @Nonnull
    @Override
    public ExplainedBoolean canProcess(@Nonnull TransportOrder order) {
        Objects.requireNonNull(order);
        return new ExplainedBoolean(true, "Supported");
    }

    @Override
    public void onVehiclePaused(boolean paused) {
        LOG.info("Vehicle paused state changed to: {}", paused);
    }

    @Override
    public void processMessage(@Nonnull VehicleCommAdapterMessage message) {
        // Not used — Kecong uses polling, not push messaging
        LOG.debug("Ignoring comm adapter message of type: {}", message.getType());
    }

    // ===== Robot interaction methods =====

    private void subscribe() {
        if (navChannel == null || navChannel.isClosed()) return;
        try {
            byte[] subData = KecongMessageEncoder.encodeSubscription(
                    new byte[]{KecongCommandCode.CMD_QUERY_ROBOT_STATUS, KecongCommandCode.CMD_QUERY_CARGO_STATUS},
                    pollIntervalMs, SUBSCRIPTION_DURATION_MS, false, subscriptionUuid);
            boolean ok = navChannel.sendAndVerify(KecongCommandCode.CMD_SUBSCRIPTION, subData);
            if (ok) LOG.debug("Subscription registered: {}", subscriptionUuid);
            else LOG.warn("Failed to register subscription: {}", subscriptionUuid);
        } catch (IOException e) {
            LOG.warn("Subscription error: {}", e.getMessage());
        }
    }

    private void refreshSubscription() {
        if (!enabled || navChannel == null || navChannel.isClosed()) return;
        subscribe();
    }

    private boolean initAutoMode() {
        if (navChannel == null || navChannel.isClosed()) return false;
        try {
            byte[] statusData = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_ROBOT_STATUS, new byte[0]);
            if (statusData == null) return false;
            RobotStatus status = KecongMessageDecoder.decodeRobotStatus(statusData);
            if (status == null) return false;
            if (status.isAutoMode() && status.getLocalizationStatus() == 3) {
                LOG.info("Robot already in auto mode with localization complete");
                processModel.setAutoReady(true);
                processModel.setState(Vehicle.State.IDLE);
                return true;
            }
            byte[] manualModeData = encodeVariableWrite("NaviControl", new byte[]{0, 0, 0, 0});
            qrChannel.sendAndVerify(KecongCommandCode.CMD_WRITE_VAR, manualModeData);
            navChannel.sendAndVerify(KecongCommandCode.CMD_MANUAL_POSITION, new byte[0]);
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            for (int i = 0; i < 30; i++) {
                statusData = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_ROBOT_STATUS, new byte[0]);
                if (statusData != null) {
                    status = KecongMessageDecoder.decodeRobotStatus(statusData);
                    if (status != null && status.getLocalizationStatus() == 3) break;
                }
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
            navChannel.sendAndVerify(KecongCommandCode.CMD_CONFIRM_POSITION, new byte[0]);
            byte[] autoModeData = encodeVariableWrite("NaviControl", new byte[]{1, 0, 0, 0});
            qrChannel.sendAndVerify(KecongCommandCode.CMD_WRITE_VAR, autoModeData);
            LOG.info("Auto mode initialization complete");
            processModel.setAutoReady(true);
            processModel.setState(Vehicle.State.IDLE);
            return true;
        } catch (IOException e) {
            LOG.error("Auto mode init error: {}", e.getMessage(), e);
            return false;
        }
    }

    private void pollRobotStatus() {
        if (!enabled || navChannel == null || navChannel.isClosed()) return;
        try {
            byte[] statusData = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_ROBOT_STATUS, new byte[0]);
            if (statusData == null) {
                if (!processModel.isAutoReady()) initAutoMode();
                return;
            }
            RobotStatus status = KecongMessageDecoder.decodeRobotStatus(statusData);
            if (status == null) return;
            lastStatusUpdateTime = System.currentTimeMillis();

            // Update process model with Pose (replaces setVehiclePosition + setOrientationAngle in 7.x)
            Triple pos = new Triple(
                    (long) (status.getPositionX() * 1000),
                    (long) (status.getPositionY() * 1000), 0);
            processModel.setPose(new Pose(pos, status.getHeadingAngle()));
            processModel.setState(translateAgvState(status));
            processModel.setKecongWorkMode(status.getWorkMode());
            processModel.setKecongAgvState(status.getAgvState());
            processModel.setLocalizationStatus(status.getLocalizationStatus());
            processModel.setConfidence(status.getConfidence());
            processModel.setBatteryPercent(status.getBatteryPercent());
            processModel.setChargeStatus(status.getChargeStatus());
            processModel.setEnergyLevel((int) (status.getBatteryPercent() * 100));
            processModel.setCmdSequence(navChannel.getSequenceNumber());

            if (status.hasError()) {
                String errorCodes = Arrays.stream(status.getAbnormalEvents())
                        .filter(ab -> ab.isError())
                        .map(ab -> String.format("0x%04X", ab.getEventCode()))
                        .collect(Collectors.joining(","));
                processModel.setErrorCodes(errorCodes);
                if (processModel.getState() != Vehicle.State.EXECUTING) {
                    processModel.setState(Vehicle.State.ERROR);
                }
                LOG.warn("Robot has errors: {}", errorCodes);
            }
            LOG.trace("Robot status: {}", status);
        } catch (IOException e) {
            LOG.debug("Poll error: {}", e.getMessage());
        }
    }

    /**
     * Build a Kecong navigation task from a single openTCS movement command step.
     * In 7.x, MovementCommand no longer has getRoute() — we process one step at a time.
     */
    private NavigationTask buildNavigationTask(MovementCommand cmd) {
        Route.Step step = cmd.getStep();
        Point destPoint = step.getDestinationPoint();
        if (destPoint == null) return null;

        int orderId = currentOrderId + 1;
        int taskKey = 1;

        NavigationTask.Builder taskBuilder = NavigationTask.builder()
                .orderId(orderId)
                .taskKey(taskKey)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE);

        int pointId = extractPointId(destPoint);
        TaskPoint tp = TaskPoint.builder()
                .sequenceNumber(0)
                .pointId(pointId)
                .build();
        taskBuilder.addPoint(tp);

        // Add action at destination
        String operation = cmd.getOperation();
        if (operation != null && !operation.isEmpty()) {
            TaskAction action = createActionForOperation(operation);
            if (action != null) {
                List<TaskPoint> points = new ArrayList<>(taskBuilder.build().getPoints());
                TaskPoint modified = TaskPoint.builder()
                        .sequenceNumber(tp.getSequenceNumber())
                        .pointId(tp.getPointId())
                        .addAction(action)
                        .build();
                points.set(0, modified);
                taskBuilder.points(points);
            }
        }
        return taskBuilder.build();
    }

    protected int extractPointId(Point point) {
        try { return Integer.parseInt(point.getName()); }
        catch (NumberFormatException e) {
            Map<String, String> props = point.getProperties();
            if (props.containsKey("kecong:pointId")) return Integer.parseInt(props.get("kecong:pointId"));
            return Math.abs(point.getName().hashCode() % 100000);
        }
    }

    private TaskAction createActionForOperation(String operation) {
        switch (operation.toUpperCase()) {
            case "LOAD": case "PICKUP":
                return new TaskAction(KecongActionType.ACTION_PALLET_LIFT, KecongActionType.CONCURRENT_SINGLE,
                        (int) (System.currentTimeMillis() % Integer.MAX_VALUE), new byte[]{1, 0, 0, 0});
            case "UNLOAD": case "DROPOFF":
                return new TaskAction(KecongActionType.ACTION_PALLET_LIFT, KecongActionType.CONCURRENT_SINGLE,
                        (int) (System.currentTimeMillis() % Integer.MAX_VALUE), new byte[]{2, 0, 0, 0});
            case "CHARGE": return null;
            default: return null;
        }
    }

    private Vehicle.State translateAgvState(RobotStatus status) {
        switch (status.getAgvState()) {
            case 0: return Vehicle.State.IDLE;
            case 1: return Vehicle.State.EXECUTING;
            case 2: return Vehicle.State.IDLE;
            case 6: return Vehicle.State.ERROR;
            case 3: return Vehicle.State.UNAVAILABLE;
            default: return Vehicle.State.UNKNOWN;
        }
    }

    private void waitForTaskCompletion(MovementCommand cmd) {
        int maxWaitMs = 600_000;
        int checkInterval = 500;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxWaitMs) {
            if (!enabled) return;
            RobotStatus status = getLatestStatus();
            if (status == null) { try { Thread.sleep(checkInterval); } catch (InterruptedException ignored) {} continue; }
            if (status.getOrderId() == 0 || status.getOrderId() != currentOrderId) {
                sentCommands.remove(cmd);
                processModel.setState(Vehicle.State.IDLE);
                LOG.info("Navigation task completed: orderId={}", currentOrderId);
                return;
            }
            if (status.hasError() || status.isNavFailed()) {
                LOG.error("Navigation failed during task execution");
                processModel.setState(Vehicle.State.ERROR);
                return;
            }
            try { Thread.sleep(checkInterval); } catch (InterruptedException ignored) {}
        }
        LOG.warn("Task completion wait timeout after {}ms", maxWaitMs);
        processModel.setState(Vehicle.State.ERROR);
    }

    private RobotStatus getLatestStatus() {
        try {
            if (navChannel == null || navChannel.isClosed()) return null;
            byte[] data = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_ROBOT_STATUS, new byte[0]);
            if (data == null) return null;
            return KecongMessageDecoder.decodeRobotStatus(data);
        } catch (IOException e) { return null; }
    }

    private byte[] encodeVariableWrite(String varName, byte[] value) {
        byte[] data = new byte[272];
        byte[] nameBytes = varName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, data, 0, Math.min(nameBytes.length, 16));
        if (value != null) System.arraycopy(value, 0, data, 16, Math.min(value.length, 256));
        return data;
    }

    public String getName() { return processModel.getName(); }
}
