package com.kecong.opentcs;

import com.kecong.opentcs.protocol.*;
import com.kecong.opentcs.protocol.model.NavigationTask;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskAction;
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
import org.opentcs.util.ExplainedBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class KecongCommAdapter implements VehicleCommAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(KecongCommAdapter.class);
    private static final String DEFAULT_NAV_HOST = "192.168.100.178";
    private static final String DEFAULT_QR_HOST = "192.168.100.200";
    private static final int DEFAULT_NAV_PORT = 17804;
    private static final int DEFAULT_QR_PORT = 17800;
    private static final int DEFAULT_POLL_INTERVAL = 100;
    private static final int SUBSCRIPTION_DURATION_MS = 60_000;
    private static final long SUBSCRIPTION_REFRESH_MS = 30_000;

    private final KecongVehicleProcessModel processModel;
    private final String navHost, qrHost;
    private final int navPort, qrPort, pollIntervalMs;
    private final byte[] authCode;
    private final boolean autoInit;

    private KecongUdpChannel navChannel, qrChannel;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture, subscriptionFuture;
    private String subscriptionUuid;
    private volatile boolean initialized, enabled;

    // Single-command tracking
    private final Queue<MovementCommand> sentCommands = new ArrayDeque<>(1);
    private int currentOrderId, currentTaskKey;
    private boolean initialPositionReported;
    private long lastPositionX = Long.MIN_VALUE, lastPositionY = Long.MIN_VALUE;

    public KecongCommAdapter(KecongVehicleProcessModel processModel,
                             String navHost, int navPort, int qrPort, String qrHost,
                             String authCodeStr, int pollIntervalMs, boolean autoInit) {
        this.processModel = Objects.requireNonNull(processModel);
        this.navHost = navHost != null ? navHost : DEFAULT_NAV_HOST;
        this.qrHost = qrHost != null ? qrHost : DEFAULT_QR_HOST;
        this.navPort = navPort > 0 ? navPort : DEFAULT_NAV_PORT;
        this.qrPort = qrPort > 0 ? qrPort : DEFAULT_QR_PORT;
        this.authCode = (authCodeStr != null && !authCodeStr.isEmpty())
                ? Arrays.copyOf(authCodeStr.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 16)
                : KecongUdpChannel.DEFAULT_AUTH_CODE.clone();
        this.pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : DEFAULT_POLL_INTERVAL;
        this.autoInit = autoInit;
    }

    @Override public void initialize() {
        if (initialized) return;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "kecong-poller"); t.setDaemon(true); return t;
        });
        this.subscriptionUuid = UUID.randomUUID().toString();
        initialized = true;
    }
    @Override public boolean isInitialized() { return initialized; }
    @Override public void terminate() { if (initialized) { disable(); scheduler.shutdownNow(); initialPositionReported = false; initialized = false; } }

    @Override public synchronized void enable() {
        if (!initialized || enabled) return;
        try {
            navChannel = new KecongUdpChannel(navHost, navPort, authCode, pollIntervalMs * 2);
            qrChannel = new KecongUdpChannel(qrHost, qrPort, authCode, pollIntervalMs * 2);
            // Auto-initialize controller if enabled via vehicle property
            if (autoInit) {
                initializeController();
            } else {
                LOG.info("autoInit disabled — assuming controller already in auto mode and localized");
            }
            pollFuture = scheduler.scheduleAtFixedRate(this::pollRobotStatus, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
            // Subscription (0xB1) not needed for "调度" protocol (0x17 polling)
            // subscriptionFuture = scheduler.scheduleAtFixedRate(this::refreshSubscription, 0, SUBSCRIPTION_REFRESH_MS, TimeUnit.MILLISECONDS);
            enabled = true;
        } catch (IOException e) { LOG.error("Enable failed", e); }
    }
    @Override public synchronized void disable() {
        if (!enabled) return;
        enabled = false;
        if (pollFuture != null) { pollFuture.cancel(false); pollFuture = null; }
        if (subscriptionFuture != null) { subscriptionFuture.cancel(false); subscriptionFuture = null; }
        if (navChannel != null) { navChannel.close(); navChannel = null; }
        if (qrChannel != null) { qrChannel.close(); qrChannel = null; }
        sentCommands.clear();
        processModel.setState(Vehicle.State.UNKNOWN);
    }
    @Override public boolean isEnabled() { return enabled; }
    @Nonnull @Override public VehicleProcessModel getProcessModel() { return processModel; }

    @Override public VehicleProcessModelTO createTransferableProcessModel() {
        return new VehicleProcessModelTO();
    }

    @Override public synchronized boolean enqueueCommand(@Nonnull MovementCommand cmd) {
        Objects.requireNonNull(cmd);
        if (!enabled) throw new IllegalStateException("Not enabled");
        if (sentCommands.size() >= 1) return false;

        Route.Step step = cmd.getStep();
        LOG.info("enqueueCommand: stepIdx={}, dest={}, op={}",
                step.getRouteIndex(), step.getDestinationPoint().getName(), cmd.getOperation());

        byte[] navData = buildNavControlData(cmd);
        if (navData == null) return false;

        try {
            boolean ok = navChannel.sendAndVerify(KecongCommandCode.CMD_NAV_CONTROL, navData);
            if (ok) {
                sentCommands.add(cmd);
                processModel.setState(Vehicle.State.EXECUTING);
                LOG.info("Nav task (0x16) dispatched: orderId={}, dest={}", currentOrderId, cmd.getStep().getDestinationPoint().getName());
                return true;
            }
        } catch (IOException e) { LOG.error("Dispatch error", e); }
        return false;
    }

    @Override public boolean canAcceptNextCommand() {
        return enabled && sentCommands.isEmpty();
    }

    @Override public Queue<MovementCommand> getUnsentCommands() { return new ArrayDeque<>(); }
    @Override public Queue<MovementCommand> getSentCommands() { return sentCommands; }
    @Override public int getCommandsCapacity() { return 1; }
    @Override public String getRechargeOperation() { return "CHARGE"; }

    @Override public synchronized void clearCommandQueue() {
        sentCommands.clear(); currentOrderId = 0; currentTaskKey = 0;
    }

    @Override public ExplainedBoolean canProcess(@Nonnull TransportOrder order) {
        return new ExplainedBoolean(true, "Supported");
    }

    @Override public void onVehiclePaused(boolean paused) {}
    @Override public void processMessage(@Nonnull VehicleCommAdapterMessage message) {}

    // --- Controller Initialization ---
    /**
     * Initialize the real controller before starting normal polling.
     * Sequence: query position → manual mode → manual position → confirm → auto mode.
     * This brings localizationStatus from 1(success) to 3(done), allowing nav task execution.
     */
    private void initializeController() throws IOException {
        LOG.info("Initializing controller...");

        // Step 1: Query current position
        RobotStatus initStatus = null;
        for (int retry = 0; retry < 5; retry++) {
            byte[] data = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_RUN_STATUS, new byte[0]);
            if (data != null) {
                initStatus = KecongMessageDecoder.decodeRunStatus(data);
                if (initStatus != null) break;
            }
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        if (initStatus == null) {
            LOG.warn("Cannot get initial position, skipping controller init");
            return;
        }
        double px = initStatus.getPositionX();
        double py = initStatus.getPositionY();
        double heading = initStatus.getHeadingAngle();
        LOG.info("Controller position: ({}, {}), heading={}", px, py, heading);

        // Step 2: Switch to manual mode (0x11: 4-byte payload)
        LOG.info("Switching to manual mode...");
        navChannel.sendAndVerify(KecongCommandCode.CMD_AUTO_MANUAL_SWITCH, new byte[]{0, 0, 0, 0});
        sleepMs(200);

        // Step 3: Manual positioning (0x14: DOUBLE format, 24 bytes)
        LOG.info("Sending manual position...");
        navChannel.sendAndVerify(KecongCommandCode.CMD_MANUAL_POSITION, encodeManualPositionDouble(px, py, heading));
        sleepMs(400);

        // Step 4: Confirm position (0x1F)
        LOG.info("Confirming position...");
        navChannel.sendAndVerify(KecongCommandCode.CMD_CONFIRM_POSITION, new byte[0]);
        sleepMs(200);

        // Step 5: Switch to auto mode (0x11: 4-byte payload)
        LOG.info("Switching to auto mode...");
        navChannel.sendAndVerify(KecongCommandCode.CMD_AUTO_MANUAL_SWITCH, new byte[]{1, 0, 0, 0});
        sleepMs(300);

        LOG.info("Controller initialization complete");
    }

    private static byte[] encodeManualPositionDouble(double x, double y, double heading) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(24);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(x);
        buf.putDouble(y);
        buf.putDouble(heading);
        return buf.array();
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // --- Polling ---
    private void refreshSubscription() {
        if (!enabled || navChannel == null || navChannel.isClosed()) return;
        try {
            byte[] sd = KecongMessageEncoder.encodeSubscription(
                    new byte[]{KecongCommandCode.CMD_QUERY_ROBOT_STATUS, KecongCommandCode.CMD_QUERY_CARGO_STATUS},
                    pollIntervalMs, SUBSCRIPTION_DURATION_MS, false, subscriptionUuid);
            navChannel.sendAndVerify(KecongCommandCode.CMD_SUBSCRIPTION, sd);
        } catch (IOException ignored) {}
    }

    private void pollRobotStatus() {
        if (!enabled || navChannel == null || navChannel.isClosed()) return;
        try {
            byte[] data = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_RUN_STATUS, new byte[0]);
            if (data == null) return;
            RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
            if (st == null) return;

            long px = (long) (st.getPositionX() * 1000);
            long py = (long) (st.getPositionY() * 1000);
            Pose pose = new Pose(new Triple(px, py, 0), st.getHeadingAngle());

            boolean hasPending = !sentCommands.isEmpty();
            boolean taskFinished = hasPending
                    && (st.getOrderId() == 0
                        || st.getOrderId() != currentOrderId
                        || st.isNavDone()
                        || st.isNavTaskFailed());

            if (taskFinished) {
                // AGV arrived (or task failed) — signal kernel
                LOG.info("Task completed: orderId={}, navTaskState={}", currentOrderId, st.getNavTaskState());
                MovementCommand cmd = sentCommands.poll();
                currentOrderId = 0;
                currentTaskKey = 0;
                processModel.setPose(pose);
                processModel.positionResolutionRequested(pose);
                processModel.commandExecuted(cmd);
                initialPositionReported = false;
            } else if (hasPending) {
                // Moving — suppress position reports, only update internal state
                processModel.setPose(pose);
            } else {
                // Idle — report position on first poll or when it actually changes
                processModel.setPose(pose);
                boolean posChanged = (px != lastPositionX || py != lastPositionY);
                if (!initialPositionReported || posChanged) {
                    processModel.positionResolutionRequested(pose);
                    if (!initialPositionReported) {
                        // Request integration on first successful position report
                        processModel.integrationLevelChangeRequested(
                                org.opentcs.data.model.Vehicle.IntegrationLevel.TO_BE_UTILIZED);
                    }
                    initialPositionReported = true;
                    lastPositionX = px;
                    lastPositionY = py;
                }
            }

            processModel.setState(translateState(st));
            processModel.setEnergyLevel((int) (st.getBatteryPercent() * 100));
            updateKecongProps(st);

            if (st.hasError()) {
                LOG.warn("Robot errors: {}", Arrays.stream(st.getAbnormalEvents())
                        .filter(ab -> ab.isError()).map(ab -> String.format("0x%04X", ab.getEventCode()))
                        .collect(Collectors.joining(",")));
            }
        } catch (IOException ignored) {}
    }

    private void updateKecongProps(RobotStatus st) {
        processModel.setKecongWorkMode(st.getWorkMode());
        processModel.setKecongAgvState(st.getAgvState());
        processModel.setLocalizationStatus(st.getLocalizationStatus());
        processModel.setConfidence(st.getConfidence());
        processModel.setBatteryPercent(st.getBatteryPercent());
        processModel.setChargeStatus(st.getChargeStatus());
        if (navChannel != null) processModel.setCmdSequence(navChannel.getSequenceNumber());
    }

    private Vehicle.State translateState(RobotStatus st) {
        switch (st.getAgvState()) {
            case 0: return Vehicle.State.IDLE;
            case 1: return Vehicle.State.EXECUTING;
            case 2: return Vehicle.State.IDLE;
            case 6: return Vehicle.State.ERROR;
            default: return Vehicle.State.UNKNOWN;
        }
    }

    // --- Navigation (0x16 NAV_CONTROL per "调度" protocol) ---
    private byte[] buildNavControlData(MovementCommand cmd) {
        Point dest = cmd.getStep().getDestinationPoint();
        if (dest == null) return null;
        currentOrderId = currentOrderId + 1;
        String ptId = dest.getName();
        String op = cmd.getOperation();
        // 0=start navigation (immediate execution for both NOP and actions)
        return KecongMessageEncoder.encodeNavControl(ptId, 0);
    }

    /**
     * @deprecated Legacy method, replaced by buildNavControlData for 0x16 protocol.
     */
    @Deprecated
    private NavigationTask buildNavigationTask(MovementCommand cmd) {
        Point dest = cmd.getStep().getDestinationPoint();
        Point src = cmd.getStep().getSourcePoint();
        if (dest == null) return null;
        int destId = extractPointId(dest);
        TaskPoint[] taskPoints;
        if (src != null && !src.getName().equals(dest.getName())) {
            int srcId = extractPointId(src);
            taskPoints = new TaskPoint[]{
                TaskPoint.builder().sequenceNumber(0).pointId(srcId).build(),
                TaskPoint.builder().sequenceNumber(1).pointId(destId).build()
            };
        } else {
            taskPoints = new TaskPoint[]{
                TaskPoint.builder().sequenceNumber(0).pointId(destId).build()
            };
        }
        NavigationTask.Builder b = NavigationTask.builder()
                .orderId(currentOrderId + 1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE);
        for (TaskPoint tp : taskPoints) b.addPoint(tp);
        String op = cmd.getOperation();
        if (op != null && !op.isEmpty()) {
            TaskAction act = createAction(op);
            if (act != null) {
                int lastSeq = taskPoints.length - 1;
                NavigationTask.Builder b2 = NavigationTask.builder()
                        .orderId(currentOrderId + 1).taskKey(1)
                        .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE);
                for (int i = 0; i < taskPoints.length; i++) {
                    TaskPoint tp = taskPoints[i];
                    if (i == lastSeq) {
                        tp = TaskPoint.builder().sequenceNumber(i).pointId(tp.getPointId()).addAction(act).build();
                    }
                    b2.addPoint(tp);
                }
                b = b2;
            }
        }
        return b.build();
    }

    private int extractPointId(Point p) {
        try { return Integer.parseInt(p.getName()); } catch (NumberFormatException e) {
            Map<String,String> props = p.getProperties();
            return props.containsKey("kecong:pointId") ? Integer.parseInt(props.get("kecong:pointId"))
                    : Math.abs(p.getName().hashCode() % 100000);
        }
    }

    private TaskAction createAction(String op) {
        switch (op.toUpperCase()) {
            case "LOAD": case "PICKUP":
                return new TaskAction(KecongActionType.ACTION_PALLET_LIFT, KecongActionType.CONCURRENT_SINGLE,
                        (int)(System.currentTimeMillis() % Integer.MAX_VALUE), new byte[]{1,0,0,0});
            case "UNLOAD": case "DROPOFF":
                return new TaskAction(KecongActionType.ACTION_PALLET_LIFT, KecongActionType.CONCURRENT_SINGLE,
                        (int)(System.currentTimeMillis() % Integer.MAX_VALUE), new byte[]{2,0,0,0});
            default: return null;
        }
    }
}
