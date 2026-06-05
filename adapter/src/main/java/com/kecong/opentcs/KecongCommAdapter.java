package com.kecong.opentcs;

import com.kecong.opentcs.protocol.*;
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
    private final int fixedEnergyLevel; // 0=use controller value, >0=override

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

    // Lift operation tracking
    private boolean liftPending;
    private String liftLimitVar; // "Button.TopLimit" or "Button.DownLimit"
    private long liftStartTime;  // System.currentTimeMillis() when lift started
    private static final long LIFT_TIMEOUT_MS = 30_000;

    public KecongCommAdapter(KecongVehicleProcessModel processModel,
                             String navHost, int navPort, int qrPort, String qrHost,
                             String authCodeStr, int pollIntervalMs, boolean autoInit,
                             int fixedEnergyLevel) {
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
        this.fixedEnergyLevel = fixedEnergyLevel;
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
            qrChannel = new KecongUdpChannel(qrHost, qrPort, authCode, 1000);
            // Auto-initialize controller if enabled via vehicle property
            if (autoInit) {
                initializeController();
            } else {
                LOG.info("autoInit disabled - assuming controller already in auto mode and localized");
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
        liftPending = false;
        liftLimitVar = null;
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
        String op = cmd.getOperation();
        LOG.info("enqueueCommand: stepIdx={}, dest={}, op={}",
                step.getRouteIndex(), step.getDestinationPoint().getName(), op);

        try {
            // Handle LOAD/UNLOAD via variable writes
            if ("LOAD".equalsIgnoreCase(op) || "PICKUP".equalsIgnoreCase(op)) {
                return startLiftOperation(cmd, "Screen.ForkUp", "Button.TopLimit");
            } else if ("UNLOAD".equalsIgnoreCase(op) || "DROPOFF".equalsIgnoreCase(op)) {
                return startLiftOperation(cmd, "Screen.ForkDown", "Button.DownLimit");
            }

            // Handle NOP as navigation command (0x16)
            byte[] navData = buildNavControlData(cmd);
            if (navData == null) return false;
            LOG.info("NAV SEND 0x16: dest={} data[{}]={}", step.getDestinationPoint().getName(),
                    navData.length, bytesToHex(java.util.Arrays.copyOf(navData, Math.min(navData.length, 16))));
            boolean ok = navChannel.sendAndVerify(KecongCommandCode.CMD_NAV_CONTROL, navData);
            LOG.info("NAV RESULT: {}", ok ? "SUCCESS" : "FAILED");
            if (ok) {
                sentCommands.add(cmd);
                processModel.setState(Vehicle.State.EXECUTING);
                LOG.info("NAV DISPATCHED: orderId={}, dest={}", currentOrderId, step.getDestinationPoint().getName());
                return true;
            }
        } catch (IOException e) { LOG.error("Dispatch error", e); }
        return false;
    }

    private boolean startLiftOperation(MovementCommand cmd, String controlVar, String limitVar) throws IOException {
        LOG.info("LIFT START: {} -> wait {}", controlVar, limitVar);
        byte[] ctrlData = new byte[17];
        byte[] ctrlBytes = controlVar.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(ctrlBytes, 0, ctrlData, 0, Math.min(ctrlBytes.length, 16));
        ctrlData[16] = 1; // true
        LOG.info("LIFT WRITE_VAR port={} cmd=0x00 data[{}]={}", qrPort, ctrlData.length, bytesToHex(ctrlData));

        boolean ok = qrChannel.sendAndVerify(KecongCommandCode.CMD_WRITE_VAR, ctrlData);
        LOG.info("LIFT WRITE_VAR result: {}", ok ? "SUCCESS" : "FAILED");
        if (!ok) {
            LOG.warn("LIFT FAILED: WRITE_VAR {} returned non-success or timeout", controlVar);
            return false;
        }
        sentCommands.add(cmd);
        liftPending = true;
        liftLimitVar = limitVar;
        liftStartTime = System.currentTimeMillis();
        processModel.setState(Vehicle.State.EXECUTING);
        LOG.info("LIFT WAITING for limit: {} (timeout={}s)", limitVar, LIFT_TIMEOUT_MS / 1000);
        return true;
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
        liftPending = false; liftLimitVar = null;
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

    private void checkLiftLimit() throws IOException {
        // Timeout protection: fail lift if limit not reached within LIFT_TIMEOUT_MS
        if (System.currentTimeMillis() - liftStartTime > LIFT_TIMEOUT_MS) {
            LOG.warn("LIFT TIMEOUT: {} not reached after {}s — forcing completion",
                    liftLimitVar, LIFT_TIMEOUT_MS / 1000);
            MovementCommand cmd = sentCommands.poll();
            liftPending = false;
            liftLimitVar = null;
            processModel.commandExecuted(cmd);
            initialPositionReported = false;
            return;
        }

        byte[] readReq = new byte[16];
        byte[] nameBytes = liftLimitVar.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, readReq, 0, Math.min(nameBytes.length, 16));

        byte[] resp = qrChannel.sendAndGetData(KecongCommandCode.CMD_READ_VAR, readReq);
        if (resp == null) {
            LOG.debug("LIFT READ_VAR {}: timeout/null response", liftLimitVar);
            return;
        }
        // Log raw response for diagnosis
        String respHex = resp.length > 0 ? bytesToHex(resp) : "(empty)";
        boolean hasValue = resp.length >= 17;
        int valByte = hasValue ? (resp[16] & 0xFF) : -1;
        byte[] nameEcho = resp.length >= 16 ? java.util.Arrays.copyOf(resp, 16) : new byte[0];
        String nameEchoStr = new String(nameEcho, java.nio.charset.StandardCharsets.US_ASCII).trim();

        if (!hasValue) {
            LOG.warn("LIFT READ_VAR {}: response too short ({}B), no value byte. hex={}",
                    liftLimitVar, resp.length, respHex);
            return;
        }
        LOG.info("LIFT READ_VAR {}: echoed='{}' valByte={} respLen={} hex={}",
                liftLimitVar, nameEchoStr, valByte, resp.length, respHex);

        if (valByte != 0) {
            LOG.info("LIFT DONE: {} limit reached (value={})", liftLimitVar, valByte);
            MovementCommand cmd = sentCommands.poll();
            liftPending = false;
            liftLimitVar = null;
            processModel.commandExecuted(cmd);
            initialPositionReported = false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
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
            // Check limit switches for pending lift operations
            if (liftPending && liftLimitVar != null) {
                checkLiftLimit();
                return;
            }

            byte[] data = navChannel.sendAndGetData(KecongCommandCode.CMD_QUERY_RUN_STATUS, new byte[0]);
            if (data == null) {
                LOG.trace("POLL 0x17: timeout");
                return;
            }
            RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
            if (st == null) {
                LOG.warn("POLL 0x17: decode failed, dataLen={}", data.length);
                return;
            }

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
                // AGV arrived (or task failed) - signal kernel
                LOG.info("Task completed: orderId={}, navTaskState={}", currentOrderId, st.getNavTaskState());
                MovementCommand cmd = sentCommands.poll();
                currentOrderId = 0;
                currentTaskKey = 0;
                processModel.setPose(pose);
                processModel.positionResolutionRequested(pose);
                processModel.commandExecuted(cmd);
                initialPositionReported = false;
            } else if (hasPending) {
                // Moving - suppress position reports, only update internal state
                processModel.setPose(pose);
            } else {
                // Idle - report position on first poll or when it actually changes
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
            int energy = fixedEnergyLevel > 0 ? fixedEnergyLevel : (int) (st.getBatteryPercent() * 100);
            processModel.setEnergyLevel(energy);
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
        // Use kc:markerId (the raw Kecong point ID, e.g. "1") so the
        // 0x16 command references the controller's own map points, not
        // the openTCS display name ("KC-1").
        String ptId = dest.getProperty("kc:markerId");
        if (ptId == null || ptId.isEmpty()) {
            ptId = dest.getName();
        }
        String op = cmd.getOperation();
        // 0=start navigation (immediate execution for both NOP and actions)
        return KecongMessageEncoder.encodeNavControl(ptId, 0);
    }

}
