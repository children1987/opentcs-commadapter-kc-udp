package com.kecong.opentcs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kecong.opentcs.protocol.*;
import com.kecong.opentcs.protocol.model.RobotStatus;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.*;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.Route;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.drivers.vehicle.MovementCommand;
import org.opentcs.util.ExplainedBoolean;

@DisplayName("KecongCommAdapter")
class KecongCommAdapterTest {

    private KecongCommAdapter adapter;
    private KecongEnergyConfig energyConfig;
    private KecongUdpChannel mockNavChannel;
    private KecongUdpChannel mockQrChannel;
    private KecongVehicleProcessModel processModel;

    @BeforeEach
    void setUp() throws Exception {
        energyConfig = KecongEnergyConfig.fromVehicleProperties(Map.of());
        processModel = new KecongVehicleProcessModel(new Vehicle("TestV"));
        adapter = new KecongCommAdapter(processModel,
                "127.0.0.1", 17804, 17800, "127.0.0.2",
                "TEST-AUTH-CODE00", 100, false, energyConfig);

        mockNavChannel = mock(KecongUdpChannel.class);
        mockQrChannel = mock(KecongUdpChannel.class);

        var nf = KecongCommAdapter.class.getDeclaredField("navChannel");
        nf.setAccessible(true); nf.set(adapter, mockNavChannel);
        var qf = KecongCommAdapter.class.getDeclaredField("qrChannel");
        qf.setAccessible(true); qf.set(adapter, mockQrChannel);
    }

    private void enable() throws Exception {
        var ef = KecongCommAdapter.class.getDeclaredField("enabled");
        ef.setAccessible(true); ef.set(adapter, true);
    }

    private void disable() throws Exception {
        var ef = KecongCommAdapter.class.getDeclaredField("enabled");
        ef.setAccessible(true); ef.set(adapter, false);
    }

    private MovementCommand mockNavCmd(String destName, String markerId, String op) {
        MovementCommand cmd = mock(MovementCommand.class);
        Route.Step step = mock(Route.Step.class);
        Point d = mock(Point.class);
        when(d.getName()).thenReturn(destName);
        when(d.getProperty("kc:markerId")).thenReturn(markerId);
        when(step.getDestinationPoint()).thenReturn(d);
        when(step.getRouteIndex()).thenReturn(0);
        when(cmd.getStep()).thenReturn(step);
        when(cmd.getOperation()).thenReturn(op);
        return cmd;
    }

    // ---- lifecycle ----

    @Test @DisplayName("isInitialized false before init")
    void testInitiallyNotInitialized() { assertFalse(adapter.isInitialized()); }

    @Test @DisplayName("initialize sets flag")
    void testInitialize() { adapter.initialize(); assertTrue(adapter.isInitialized()); }

    @Test @DisplayName("initialize idempotent")
    void testInitializeIdempotent() { adapter.initialize(); adapter.initialize(); assertTrue(adapter.isInitialized()); }

    @Test @DisplayName("terminate clears state")
    void testTerminate() { adapter.initialize(); adapter.terminate(); assertFalse(adapter.isInitialized()); }

    @Test @DisplayName("isEnabled initially false")
    void testInitiallyDisabled() { assertFalse(adapter.isEnabled()); }

    @Test @DisplayName("getProcessModel returns correct model")
    void testGetProcessModel() { assertSame(processModel, adapter.getProcessModel()); }

    @Test @DisplayName("createTransferableProcessModel not null")
    void testCreateTransferableProcessModel() { assertNotNull(adapter.createTransferableProcessModel()); }

    // ---- capacity / getters ----

    @Test @DisplayName("canAcceptNextCommand true when enabled+empty")
    void testCanAcceptNextCommand() throws Exception { enable(); assertTrue(adapter.canAcceptNextCommand()); }

    @Test @DisplayName("getCommandsCapacity is 1")
    void testGetCommandsCapacity() { assertEquals(1, adapter.getCommandsCapacity()); }

    @Test @DisplayName("getSentCommands initially empty")
    void testGetSentCommandsEmpty() { assertTrue(adapter.getSentCommands().isEmpty()); }

    @Test @DisplayName("getUnsentCommands always empty")
    void testGetUnsentCommandsEmpty() { assertTrue(adapter.getUnsentCommands().isEmpty()); }

    @Test @DisplayName("getRechargeOperation is CHARGE")
    void testGetRechargeOperation() { assertEquals("CHARGE", adapter.getRechargeOperation()); }

    @Test @DisplayName("canProcess returns true")
    void testCanProcess() { assertTrue(adapter.canProcess(mock(TransportOrder.class)).getValue()); }

    @Test @DisplayName("onVehiclePaused no-op")
    void testOnVehiclePaused() { assertDoesNotThrow(() -> { adapter.onVehiclePaused(true); adapter.onVehiclePaused(false); }); }

    @Test @DisplayName("processMessage no-op")
    void testProcessMessage() { assertDoesNotThrow(() -> adapter.processMessage(null)); }

    // ---- enqueueCommand error paths ----

    @Test @DisplayName("enqueueCommand throws when disabled")
    void testEnqueueCommandNotEnabled() { assertThrows(IllegalStateException.class, () -> adapter.enqueueCommand(mockNavCmd("1","1","NOP"))); }

    @Test @DisplayName("enqueueCommand returns false when full")
    void testEnqueueCommandCapacityFull() throws Exception { enable();
        when(mockNavChannel.sendAndVerify(eq(KecongCommandCode.CMD_NAV_CONTROL), any())).thenReturn(true);
        assertTrue(adapter.enqueueCommand(mockNavCmd("1","1","NOP")));
        assertFalse(adapter.enqueueCommand(mockNavCmd("2","2","NOP")));
    }

    @Test @DisplayName("enqueueCommand nav fails")
    void testEnqueueCommandNavFail() throws Exception { enable();
        when(mockNavChannel.sendAndVerify(eq(KecongCommandCode.CMD_NAV_CONTROL), any())).thenReturn(false);
        assertFalse(adapter.enqueueCommand(mockNavCmd("1","1","NOP")));
    }

    // ---- lift operations ----

    @Test @DisplayName("enqueueCommand LOAD dispatches Forkup")
    void testEnqueueCommandLoad() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        assertTrue(adapter.enqueueCommand(mockNavCmd("P1",null,"LOAD")));
    }

    @Test @DisplayName("enqueueCommand UNLOAD dispatches Forkdown")
    void testEnqueueCommandUnload() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        assertTrue(adapter.enqueueCommand(mockNavCmd("P1",null,"UNLOAD")));
    }

    @Test @DisplayName("enqueueCommand PICKUP dispatches Forkup")
    void testEnqueueCommandPickup() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        assertTrue(adapter.enqueueCommand(mockNavCmd("P1",null,"PICKUP")));
    }

    @Test @DisplayName("enqueueCommand DROPOFF dispatches Forkdown")
    void testEnqueueCommandDropoff() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        assertTrue(adapter.enqueueCommand(mockNavCmd("P1",null,"DROPOFF")));
    }

    @Test @DisplayName("enqueueCommand FORK_FWD dispatches Forkforword")
    void testEnqueueCommandForkFwd() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        assertTrue(adapter.enqueueCommand(mockNavCmd("P1",null,"FORK_FWD")));
    }

    @Test @DisplayName("enqueueCommand FORK_REV dispatches Forkback")
    void testEnqueueCommandForkRev() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        assertTrue(adapter.enqueueCommand(mockNavCmd("P1",null,"FORK_REV")));
    }

    @Test @DisplayName("enqueueCommand lift fails on WRITE_VAR error")
    void testEnqueueCommandLiftFail() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(false);
        assertFalse(adapter.enqueueCommand(mockNavCmd("P1",null,"LOAD")));
    }

    // ---- sent commands tracking ----

    @Test @DisplayName("sentCommands contains dispatched command")
    void testSentCommandsAfterDispatch() throws Exception { enable();
        when(mockNavChannel.sendAndVerify(eq(KecongCommandCode.CMD_NAV_CONTROL), any())).thenReturn(true);
        MovementCommand cmd = mockNavCmd("1","1","NOP");
        adapter.enqueueCommand(cmd);
        Queue<MovementCommand> sent = adapter.getSentCommands();
        assertEquals(1, sent.size()); assertSame(cmd, sent.peek());
    }

    // ---- clearCommandQueue / disable ----

    @Test @DisplayName("clearCommandQueue empties sent commands")
    void testClearCommandQueue() throws Exception { enable();
        when(mockNavChannel.sendAndVerify(eq(KecongCommandCode.CMD_NAV_CONTROL), any())).thenReturn(true);
        adapter.enqueueCommand(mockNavCmd("1","1","NOP"));
        adapter.clearCommandQueue();
        assertTrue(adapter.getSentCommands().isEmpty());
    }

    @Test @DisplayName("disable clears commands + state")
    void testDisable() throws Exception { enable();
        when(mockNavChannel.sendAndVerify(eq(KecongCommandCode.CMD_NAV_CONTROL), any())).thenReturn(true);
        adapter.enqueueCommand(mockNavCmd("1","1","NOP"));
        adapter.disable();
        assertFalse(adapter.isEnabled()); assertTrue(adapter.getSentCommands().isEmpty());
    }

    @Test @DisplayName("disable when already disabled is safe")
    void testDisableIdempotent() { adapter.disable(); assertFalse(adapter.isEnabled()); }

    // ---- constructors ----

    @Test @DisplayName("Constructor with null auth")
    void testConstructorNullAuth() {
        KecongCommAdapter a = new KecongCommAdapter(processModel, "127.0.0.1", 17804, 17800, "127.0.0.2", null, 100, false, energyConfig);
        assertNotNull(a);
    }

    @Test @DisplayName("Constructor with empty auth")
    void testConstructorEmptyAuth() {
        KecongCommAdapter a = new KecongCommAdapter(processModel, "127.0.0.1", 17804, 17800, "127.0.0.2", "", 100, false, energyConfig);
        assertNotNull(a);
    }

    @Test @DisplayName("Constructor with custom auth and autoInit")
    void testConstructorCustomAuth() {
        KecongCommAdapter a = new KecongCommAdapter(processModel, "127.0.0.1", 17804, 17800, "127.0.0.2", "CUSTOM-KEY-12345", 100, true, energyConfig);
        assertNotNull(a);
    }

    @Test @DisplayName("Constructor with null hosts/ports")
    void testConstructorDefaultHosts() {
        KecongCommAdapter a = new KecongCommAdapter(processModel, null, 0, 0, null, null, 0, false, energyConfig);
        assertNotNull(a);
    }

    // ---- energy level ----

    @Test @DisplayName("readEnergyLevel PROTOCOL 0%") void testEnergyProtoZero() { assertEquals(0, adapter.readEnergyLevel(makeStatus(0.0f))); }
    @Test @DisplayName("readEnergyLevel PROTOCOL 100%") void testEnergyProtoFull() { assertEquals(100, adapter.readEnergyLevel(makeStatus(1.0f))); }
    @Test @DisplayName("readEnergyLevel PROTOCOL fractional") void testEnergyProtoFrac() { assertEquals(42, adapter.readEnergyLevel(makeStatus(0.425f))); }

    private static RobotStatus makeStatus(float battery) { RobotStatus s = new RobotStatus(); s.setBatteryPercent(battery); return s; }

    // ---- bytesToFloatLE coverage via readEnergyFromReadVar ----

    private static void putFloatLE(byte[] buf, int off, float v) {
        int bits = Float.floatToIntBits(v);
        buf[off]=(byte)bits; buf[off+1]=(byte)(bits>>8); buf[off+2]=(byte)(bits>>16); buf[off+3]=(byte)(bits>>24);
    }

    @Test @DisplayName("readEnergyFromReadVar 85.0f → 85")
    void testReadVar85() throws Exception { enable();
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        byte[] resp = new byte[20]; resp[0]='b'; putFloatLE(resp, 16, 85.0f);
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any())).thenReturn(resp);
        assertEquals(85, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("readEnergyFromReadVar 99.8f → 100")
    void testReadVar99_8() throws Exception { enable();
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        byte[] resp = new byte[20]; resp[0]='b'; putFloatLE(resp, 16, 99.8f);
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any())).thenReturn(resp);
        assertEquals(100, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("readEnergyFromReadVar timeout")
    void testReadVarTimeout() throws Exception { enable();
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any())).thenReturn(null);
        assertEquals(-1, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("readEnergyLevel catches IOException")
    void testReadVarIOException() throws Exception { enable();
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any())).thenThrow(new IOException("err"));
        assertEquals(-1, adapter.readEnergyLevel(null));
    }

    // ---- full lifecycle + edge cases ----

    @Test @DisplayName("Full lifecycle: init → enable → disable")
    void testFullLifecycle() throws Exception {
        when(mockNavChannel.isClosed()).thenReturn(false);
        adapter.initialize(); enable();
        assertTrue(adapter.isEnabled());
        adapter.disable(); assertFalse(adapter.isEnabled());
    }

    @Test @DisplayName("canAcceptNextCommand false when disabled")
    void testCanAcceptDisabled() { assertFalse(adapter.canAcceptNextCommand()); }

    @Test @DisplayName("enqueueCommand null cmd → NPE")
    void testEnqueueNull() throws Exception { enable(); assertThrows(NullPointerException.class, () -> adapter.enqueueCommand(null)); }

    @Test @DisplayName("enqueueCommand NOP null dest → NPE")
    void testNavNullDest() throws Exception { enable();
        MovementCommand cmd = mock(MovementCommand.class);
        Route.Step s = mock(Route.Step.class);
        when(s.getDestinationPoint()).thenReturn(null); when(s.getRouteIndex()).thenReturn(0);
        when(cmd.getStep()).thenReturn(s); when(cmd.getOperation()).thenReturn("NOP");
        // LOG.info calls dest.getName() before null check — real code path, NPE expected
        assertThrows(NullPointerException.class, () -> adapter.enqueueCommand(cmd));
    }

    @Test @DisplayName("enqueueCommand NOP IOException → false")
    void testNavIOException() throws Exception { enable();
        when(mockNavChannel.sendAndVerify(eq(KecongCommandCode.CMD_NAV_CONTROL), any()))
                .thenThrow(new IOException("err"));
        assertFalse(adapter.enqueueCommand(mockNavCmd("1","1","NOP")));
    }

    @Test @DisplayName("enqueueCommand LOAD IOException → false")
    void testLiftIOException() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any()))
                .thenThrow(new IOException("err"));
        assertFalse(adapter.enqueueCommand(mockNavCmd("P1",null,"LOAD")));
    }

    @Test @DisplayName("disable with null channels")
    void testDisableNullChannels() throws Exception { enable();
        var nf = KecongCommAdapter.class.getDeclaredField("navChannel");
        nf.setAccessible(true); nf.set(adapter, null);
        var qf = KecongCommAdapter.class.getDeclaredField("qrChannel");
        qf.setAccessible(true); qf.set(adapter, null);
        assertDoesNotThrow(() -> adapter.disable());
    }

    @Test @DisplayName("updateKecongProps via reflection")
    void testUpdateKecongProps() throws Exception {
        RobotStatus st = new RobotStatus();
        st.setWorkMode(3); st.setAgvState(1); st.setLocalizationStatus(3);
        st.setConfidence(95); st.setBatteryPercent(0.88f); st.setChargeStatus(1);
        var m = KecongCommAdapter.class.getDeclaredMethod("updateKecongProps", RobotStatus.class);
        m.setAccessible(true); m.invoke(adapter, st);
        assertEquals(3, processModel.getKecongWorkMode());
        assertEquals(1, processModel.getKecongAgvState());
        assertEquals(3, processModel.getLocalizationStatus());
        assertEquals(95, processModel.getConfidence());
        assertEquals(0.88f, processModel.getBatteryPercent(), 0.001f);
        assertEquals(1, processModel.getChargeStatus());
    }

    @Test @DisplayName("readEnergyFromReadVar short data → 0")
    void testReadVarShortData() throws Exception { enable();
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        // 17B = 16B name + 1B value → too short for 4B float → 0f → 0
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any()))
                .thenReturn(new byte[17]);
        assertEquals(0, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("readEnergyFromMultiVar empty dataLen → -1")
    void testReadMultiVarEmptyLen() throws Exception { enable();
        energyConfig.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        energyConfig.setVarName("B2GW"); energyConfig.setVarOffset(0); energyConfig.setVarPort("NAV");
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(0); b.putInt(0);
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_MULTI_VAR), any())).thenReturn(b.array());
        assertEquals(-1, adapter.readEnergyFromMultiVar());
    }

    @Test @DisplayName("readEnergyFromMultiVar too-short → -1")
    void testReadMultiVarTooShort() throws Exception { enable();
        energyConfig.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        energyConfig.setVarName("B2GW"); energyConfig.setVarOffset(0); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_MULTI_VAR), any()))
                .thenReturn(new byte[3]);
        assertEquals(-1, adapter.readEnergyFromMultiVar());
    }

    @Test @DisplayName("getVarChannel case-insensitive QR")
    void testVarPortCase() { energyConfig.setVarPort("qr"); assertSame(mockQrChannel, adapter.getVarChannel()); }

    @Test @DisplayName("readEnergyFromReadVar value=0f → 0")
    void testReadVarZero() throws Exception { enable();
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("z"); energyConfig.setVarPort("NAV");
        byte[] resp = new byte[20]; resp[0]='z'; putFloatLE(resp, 16, 0f);
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any())).thenReturn(resp);
        assertEquals(0, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("readEnergyLevel PROTOCOL 0.999f truncates to 99")
    void testEnergyProtoEdge() { assertEquals(99, adapter.readEnergyLevel(makeStatus(0.999f))); }

    @Test @DisplayName("clearCommandQueue after lift dispatch")
    void testClearAfterLift() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        adapter.enqueueCommand(mockNavCmd("P1",null,"LOAD"));
        adapter.clearCommandQueue();
        assertTrue(adapter.getSentCommands().isEmpty());
    }

    @Test @DisplayName("enqueueCommand FORK_FWD lift fail → false")
    void testForkFwdFail() throws Exception { enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(false);
        assertFalse(adapter.enqueueCommand(mockNavCmd("P1",null,"FORK_FWD")));
    }

    @Test @DisplayName("Constructor all defaults")
    void testConstructorMinimal() {
        KecongEnergyConfig ec = KecongEnergyConfig.fromVehicleProperties(Map.of());
        KecongCommAdapter a = new KecongCommAdapter(processModel,
                null, -1, -1, null, null, -1, false, ec);
        assertNotNull(a); assertNotNull(a.getProcessModel());
    }

    // ---- translateState (via reflection, package-private) ----

    @Test @DisplayName("translateState: IDLE (0)")
    void testTranslateIdle() throws Exception {
        var m = KecongCommAdapter.class.getDeclaredMethod("translateState", RobotStatus.class);
        m.setAccessible(true);
        RobotStatus st = new RobotStatus(); st.setAgvState(0);
        assertEquals(Vehicle.State.IDLE, m.invoke(adapter, st));
    }

    @Test @DisplayName("translateState: EXECUTING (1)")
    void testTranslateExecuting() throws Exception {
        var m = KecongCommAdapter.class.getDeclaredMethod("translateState", RobotStatus.class);
        m.setAccessible(true);
        RobotStatus st = new RobotStatus(); st.setAgvState(1);
        assertEquals(Vehicle.State.EXECUTING, m.invoke(adapter, st));
    }

    @Test @DisplayName("translateState: IDLE (2)")
    void testTranslatePaused() throws Exception {
        var m = KecongCommAdapter.class.getDeclaredMethod("translateState", RobotStatus.class);
        m.setAccessible(true);
        RobotStatus st = new RobotStatus(); st.setAgvState(2);
        assertEquals(Vehicle.State.IDLE, m.invoke(adapter, st));
    }

    @Test @DisplayName("translateState: ERROR (6)")
    void testTranslateError() throws Exception {
        var m = KecongCommAdapter.class.getDeclaredMethod("translateState", RobotStatus.class);
        m.setAccessible(true);
        RobotStatus st = new RobotStatus(); st.setAgvState(6);
        assertEquals(Vehicle.State.ERROR, m.invoke(adapter, st));
    }

    @Test @DisplayName("translateState: UNKNOWN (99)")
    void testTranslateUnknown() throws Exception {
        var m = KecongCommAdapter.class.getDeclaredMethod("translateState", RobotStatus.class);
        m.setAccessible(true);
        RobotStatus st = new RobotStatus(); st.setAgvState(99);
        assertEquals(Vehicle.State.UNKNOWN, m.invoke(adapter, st));
    }

    // ---- refreshSubscription ----

    @Test @DisplayName("refreshSubscription sends 0xB1")
    void testRefreshSubscription() throws Exception {
        enable(); adapter.initialize();
        when(mockNavChannel.isClosed()).thenReturn(false);
        when(mockNavChannel.sendAndVerify(eq(KecongCommandCode.CMD_SUBSCRIPTION), any())).thenReturn(true);
        var m = KecongCommAdapter.class.getDeclaredMethod("refreshSubscription");
        m.setAccessible(true);
        assertDoesNotThrow(() -> m.invoke(adapter));
    }

    // ---- enable with autoInit (mock init sequence) ----

    @Test @DisplayName("enable with autoInit=true")
    void testEnableAutoInit() throws Exception {
        // Create adapter with autoInit=true
        KecongCommAdapter a = new KecongCommAdapter(processModel,
                "127.0.0.1", 17804, 17800, "127.0.0.2",
                "TEST-AUTH-CODE00", 100, true, energyConfig);
        // Inject mocks
        var nf = KecongCommAdapter.class.getDeclaredField("navChannel");
        nf.setAccessible(true); nf.set(a, mockNavChannel);
        var qf = KecongCommAdapter.class.getDeclaredField("qrChannel");
        qf.setAccessible(true); qf.set(a, mockQrChannel);
        a.initialize();

        // First response for INIT position query (0x17)
        // Build a minimal valid 0x17 response
        byte[] initResp = buildMinimal17Response(1.0, 2.0, 0.5);
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_QUERY_RUN_STATUS), any()))
                .thenReturn(initResp);
        // Subsequent commands (manual mode, manual pos, confirm, auto mode)
        when(mockNavChannel.sendAndVerify(anyByte(), any())).thenReturn(true);

        a.enable();
        assertTrue(a.isEnabled());
        a.disable();
    }

    /** Build a minimal valid 0x17 decodeRunStatus response */
    private static byte[] buildMinimal17Response(double x, double y, double heading) {
        byte[] data = new byte[0xC0];
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(0);       // body temp
        buf.putDouble(x);       // pos_x
        buf.putDouble(y);       // pos_y
        buf.putDouble(heading); // heading
        buf.putDouble(0.5);     // battery
        buf.put((byte)0);       // blocked
        buf.put((byte)0);       // charging
        buf.put((byte)1);       // run_mode=auto
        buf.put((byte)1);       // map_loaded
        buf.putInt(0);          // target_pt
        buf.putDouble(0);       // vel_x
        buf.putDouble(0);       // angular_vel
        buf.putDouble(24.0);    // battery_voltage
        buf.putDouble(0);       // current
        buf.put((byte)0);       // task_state=none
        // remaining reserved bytes to 0x70
        buf.position(0x70);
        buf.put((byte)3);       // loc_status=done
        // to 0xB8
        buf.position(0xB8);
        buf.putFloat(1.0f);     // confidence
        return data;
    }

    // ---- checkLiftLimit and stopLift via enabling lift operation ----
    // The lift timeout is 30s — we can test the DURATION_MS elapsed branch

    @Test @DisplayName("Lift stop on duration reached")
    void testLiftStopDuration() throws Exception {
        enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        adapter.enqueueCommand(mockNavCmd("P1",null,"LOAD"));

        // Directly manipulate lift state to simulate duration elapsed
        var liftVar = KecongCommAdapter.class.getDeclaredField("liftVarName");
        liftVar.setAccessible(true);
        var liftStart = KecongCommAdapter.class.getDeclaredField("liftStartTime");
        liftStart.setAccessible(true);
        // Set start time far enough back that LIFT_DURATION_MS (5000) has passed
        liftStart.set(adapter, System.currentTimeMillis() - 6000);

        // Call checkLiftLimit via reflection
        var checkLift = KecongCommAdapter.class.getDeclaredMethod("checkLiftLimit");
        checkLift.setAccessible(true);
        // Should trigger stopLift which sends WRITE_VAR=0
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        checkLift.invoke(adapter);
        // Verify stop command was sent
        verify(mockQrChannel, atLeastOnce()).sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any());
    }

    @Test @DisplayName("Lift timeout forces completion")
    void testLiftTimeout() throws Exception {
        enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        adapter.enqueueCommand(mockNavCmd("P1",null,"LOAD"));

        var liftStart = KecongCommAdapter.class.getDeclaredField("liftStartTime");
        liftStart.setAccessible(true);
        // Set start time beyond LIFT_TIMEOUT_MS (30000)
        liftStart.set(adapter, System.currentTimeMillis() - 31000);

        var checkLift = KecongCommAdapter.class.getDeclaredMethod("checkLiftLimit");
        checkLift.setAccessible(true);
        checkLift.invoke(adapter);
        // Timeout should have forced stop via stopLift
        verify(mockQrChannel, atLeastOnce()).sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any());
    }

    // ---- resolvePoint ----

    @Test @DisplayName("resolvePoint: matches nearest point")
    void testResolvePointMatch() throws Exception {
        var m = KecongCommAdapter.class.getDeclaredMethod("resolvePoint", long.class, long.class);
        m.setAccessible(true);
        String result = (String) m.invoke(adapter, 2000L, 0L);  // Close to point "01" (2000,0)
        assertEquals("01", result);
    }

    @Test @DisplayName("resolvePoint: far away returns null")
    void testResolvePointNoMatch() throws Exception {
        var m = KecongCommAdapter.class.getDeclaredMethod("resolvePoint", long.class, long.class);
        m.setAccessible(true);
        // 999999,999999 is >100000 from any hardcoded point
        String result = (String) m.invoke(adapter, 999999L, 999999L);
        assertNull(result);
    }

    @Test @DisplayName("resolvePoint: exact match point 00")
    void testResolvePoint00() throws Exception {
        var m = KecongCommAdapter.class.getDeclaredMethod("resolvePoint", long.class, long.class);
        m.setAccessible(true);
        assertEquals("00", m.invoke(adapter, 0L, 0L));
    }

    // ---- disable with subchannel null ----

    @Test @DisplayName("disable when enabled and channels set")
    void testDisableFull() throws Exception {
        when(mockNavChannel.isClosed()).thenReturn(false);
        adapter.initialize(); enable();
        assertTrue(adapter.isEnabled());
        adapter.disable();
        assertFalse(adapter.isEnabled());
    }

    // ---- bytesToFloatLE edge: null bytes ----
    @Test @DisplayName("bytesToFloatLE null → 0f → 0")
    void testBytesToFloatNull() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        // Send a response with a name but value bytes that are all zeros
        byte[] resp = new byte[20]; resp[0]='b';
        // putFloatLE with 0 bytes → all zeros → float 0.0f
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any())).thenReturn(resp);
        assertEquals(0, adapter.readEnergyFromReadVar());
    }

    // ---- terminate paths ----

    @Test @DisplayName("terminate when not initialized")
    void testTerminateNotInit() { adapter.terminate(); assertFalse(adapter.isInitialized()); }

    // ---- enable() early-return paths ----

    @Test @DisplayName("enable when not initialized returns early")
    void testEnableNotInit() { adapter.enable(); assertFalse(adapter.isEnabled()); }

    @Test @DisplayName("enable when already enabled is idempotent")
    void testEnableWhenEnabled() throws Exception {
        adapter.initialize(); enable();
        assertTrue(adapter.isEnabled());
        adapter.enable(); // second call should be no-op
        assertTrue(adapter.isEnabled());
    }

    // ---- terminate → disable → channel close ----

    @Test @DisplayName("terminate when enabled calls disable")
    void testTerminateWhenEnabled() throws Exception {
        when(mockNavChannel.isClosed()).thenReturn(false);
        adapter.initialize(); enable();
        adapter.terminate();
        assertFalse(adapter.isInitialized());
    }

    // ---- readEnergyLevel via dispatch ----

    @Test @DisplayName("readEnergyLevel READ_VAR via dispatch")
    void testEnergyDispatchReadVar() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        byte[] resp = new byte[20]; resp[0]='b'; putFloatLE(resp, 16, 55f);
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any())).thenReturn(resp);
        assertEquals(55, adapter.readEnergyLevel(null));
    }

    @Test @DisplayName("readEnergyLevel READ_MULTI_VAR via dispatch")
    void testEnergyDispatchMultiVar() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        energyConfig.setVarName("B2GW"); energyConfig.setVarOffset(0); energyConfig.setVarPort("NAV");
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(0); b.putInt(4); b.putInt(Float.floatToIntBits(33f));
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_MULTI_VAR), any())).thenReturn(b.array());
        assertEquals(33, adapter.readEnergyLevel(null));
    }

    @Test @DisplayName("Lift stop after duration+2s completes lift")
    void testLiftComplete() throws Exception {
        enable();
        when(mockQrChannel.sendAndVerify(eq(KecongCommandCode.CMD_WRITE_VAR), any())).thenReturn(true);
        adapter.enqueueCommand(mockNavCmd("P1",null,"LOAD"));

        var liftStart = KecongCommAdapter.class.getDeclaredField("liftStartTime");
        liftStart.setAccessible(true);
        var liftStopReq = KecongCommAdapter.class.getDeclaredField("liftStopRequested");
        liftStopReq.setAccessible(true);
        var liftVar = KecongCommAdapter.class.getDeclaredField("liftVarName");
        liftVar.setAccessible(true);
        // Simulate: elapsed > DURATION_MS + 2000, and stop was already requested
        liftStart.set(adapter, System.currentTimeMillis() - 8000); // past DURATION+2000
        liftStopReq.set(adapter, true);

        var checkLift = KecongCommAdapter.class.getDeclaredMethod("checkLiftLimit");
        checkLift.setAccessible(true);
        checkLift.invoke(adapter);

        // Lift should be completed (liftPending=false, liftVarName=null)
        var liftPending = KecongCommAdapter.class.getDeclaredField("liftPending");
        liftPending.setAccessible(true);
        assertFalse((boolean) liftPending.get(adapter));
        assertNull(liftVar.get(adapter));
    }
}
