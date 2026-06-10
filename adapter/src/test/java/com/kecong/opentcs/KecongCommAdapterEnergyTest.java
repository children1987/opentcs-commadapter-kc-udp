package com.kecong.opentcs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kecong.opentcs.protocol.*;
import com.kecong.opentcs.protocol.model.RobotStatus;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("KecongCommAdapter — 电量读取")
class KecongCommAdapterEnergyTest {

    private KecongCommAdapter adapter;
    private KecongEnergyConfig energyConfig;
    private KecongUdpChannel mockNavChannel;
    private KecongUdpChannel mockQrChannel;

    @BeforeEach
    void setUp() throws Exception {
        energyConfig = KecongEnergyConfig.fromVehicleProperties(Map.of());
        var processModel = new KecongVehicleProcessModel(
                new org.opentcs.data.model.Vehicle("TestV"));
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

    /** Encode a float as 4 little-endian bytes for READ_VAR response data area */
    private static void putFloatLE(byte[] buf, int offset, float value) {
        int bits = Float.floatToIntBits(value);
        buf[offset]     = (byte) (bits);
        buf[offset + 1] = (byte) (bits >> 8);
        buf[offset + 2] = (byte) (bits >> 16);
        buf[offset + 3] = (byte) (bits >> 24);
    }

    /** Build a valid 0x01 response: [16B name][4B float value] */
    private static byte[] readVarResponse(String name, float value) {
        byte[] resp = new byte[20];
        byte[] nb = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nb, 0, resp, 0, Math.min(nb.length, 16));
        putFloatLE(resp, 16, value);
        return resp;
    }

    /** Build a valid 0x02 response: [U32 valueId=0][U32 dataLen=4][U32 bits of float] */
    private static byte[] readMultiVarResponse(float value) {
        ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0);                      // valueId
        buf.putInt(4);                      // dataLen
        buf.putInt(Float.floatToIntBits(value)); // float as int bits
        return buf.array();
    }

    // ---- readEnergyLevel dispatch ----

    @Test @DisplayName("PROTOCOL mode reads from RobotStatus.batteryPercent")
    void testProtocolMode() { RobotStatus st = new RobotStatus(); st.setBatteryPercent(0.85f); assertEquals(85, adapter.readEnergyLevel(st)); }
    @Test @DisplayName("PROTOCOL mode null → -1")
    void testProtocolNull() { assertEquals(-1, adapter.readEnergyLevel(null)); }
    @Test @DisplayName("PROTOCOL 100%") void testProtoFull() { RobotStatus st=new RobotStatus(); st.setBatteryPercent(1.0f); assertEquals(100, adapter.readEnergyLevel(st)); }
    @Test @DisplayName("PROTOCOL 0%") void testProtoZero() { RobotStatus st=new RobotStatus(); st.setBatteryPercent(0f); assertEquals(0, adapter.readEnergyLevel(st)); }

    // ---- READ_VAR ----

    @Test @DisplayName("READ_VAR: 90.0f → 90")
    void testReadVar90() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("battery"); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any()))
                .thenReturn(readVarResponse("battery", 90.0f));
        assertEquals(90, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("READ_VAR: 99.8f → 100 (四舍五入)")
    void testReadVar99_8() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("battery"); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any()))
                .thenReturn(readVarResponse("battery", 99.8f));
        assertEquals(100, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("READ_VAR: 42.3f → 42 (四舍五入)")
    void testReadVar42_3() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any()))
                .thenReturn(readVarResponse("b", 42.3f));
        assertEquals(42, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("READ_VAR timeout → -1")
    void testReadVarTimeout() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any())).thenReturn(null);
        assertEquals(-1, adapter.readEnergyFromReadVar());
    }

    @Test @DisplayName("READ_VAR QR channel")
    void testReadVarQr() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("QR");
        when(mockQrChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any()))
                .thenReturn(readVarResponse("b", 50f));
        assertEquals(50, adapter.readEnergyFromReadVar());
        verify(mockQrChannel).sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any());
    }

    // ---- READ_MULTI_VAR ----

    @Test @DisplayName("READ_MULTI_VAR: 75.0f → 75")
    void testReadMultiVar75() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        energyConfig.setVarName("B2GW"); energyConfig.setVarOffset(24); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_MULTI_VAR), any()))
                .thenReturn(readMultiVarResponse(75.0f));
        assertEquals(75, adapter.readEnergyFromMultiVar());
    }

    @Test @DisplayName("READ_MULTI_VAR: 99.9f → 100")
    void testReadMultiVar99_9() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        energyConfig.setVarName("B2GW"); energyConfig.setVarOffset(24); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_MULTI_VAR), any()))
                .thenReturn(readMultiVarResponse(99.9f));
        assertEquals(100, adapter.readEnergyFromMultiVar());
    }

    @Test @DisplayName("READ_MULTI_VAR timeout → -1")
    void testReadMultiVarTimeout() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        energyConfig.setVarName("B2GW"); energyConfig.setVarOffset(24); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_MULTI_VAR), any())).thenReturn(null);
        assertEquals(-1, adapter.readEnergyFromMultiVar());
    }

    @Test @DisplayName("READ_MULTI_VAR QR channel")
    void testReadMultiVarQr() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        energyConfig.setVarName("B2GW"); energyConfig.setVarOffset(0); energyConfig.setVarPort("QR");
        when(mockQrChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_MULTI_VAR), any()))
                .thenReturn(readMultiVarResponse(60f));
        assertEquals(60, adapter.readEnergyFromMultiVar());
    }

    // ---- exception handling ----

    @Test @DisplayName("READ_VAR IOException → -1")
    void testReadVarException() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_VAR);
        energyConfig.setVarName("b"); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any()))
                .thenThrow(new IOException("err"));
        assertEquals(-1, adapter.readEnergyLevel(null));
    }

    @Test @DisplayName("READ_MULTI_VAR IOException → -1")
    void testReadMultiVarException() throws Exception {
        energyConfig.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        energyConfig.setVarName("B2GW"); energyConfig.setVarOffset(0); energyConfig.setVarPort("NAV");
        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_MULTI_VAR), any()))
                .thenThrow(new IOException("err"));
        assertEquals(-1, adapter.readEnergyLevel(null));
    }

    // ---- getVarChannel ----

    @Test @DisplayName("getVarChannel NAV default") void testChannelNav() { energyConfig.setVarPort("NAV"); assertSame(mockNavChannel, adapter.getVarChannel()); }
    @Test @DisplayName("getVarChannel QR") void testChannelQr() { energyConfig.setVarPort("QR"); assertSame(mockQrChannel, adapter.getVarChannel()); }
    @Test @DisplayName("getVarChannel unknown → NAV") void testChannelUnknown() { energyConfig.setVarPort("X"); assertSame(mockNavChannel, adapter.getVarChannel()); }

    // ---- hot-reload ----

    @Test @DisplayName("Hot-reload non-existent file — no crash")
    void testHotReloadMissingFile() { energyConfig.setConfigFilePath(Path.of("/no/such/file")); assertDoesNotThrow(() -> adapter.readEnergyLevel(new RobotStatus() {{ setBatteryPercent(0.5f); }})); }

    @Test @DisplayName("Hot-reload JSON changes source")
    void testHotReloadChangesSource(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("e.json");
        Files.write(f, "{\"energySource\":\"READ_VAR\",\"energyVarName\":\"bat\"}".getBytes(StandardCharsets.UTF_8));
        energyConfig.setConfigFilePath(f);
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, energyConfig.getSource());
        assertTrue(energyConfig.reloadFromJsonFile());
        assertEquals(KecongEnergyConfig.Source.READ_VAR, energyConfig.getSource());
        assertEquals("bat", energyConfig.getVarName());
    }

    @Test @DisplayName("Full dispatch: PROTOCOL → READ_VAR after hot-reload")
    void testFullHotReloadDispatch(@TempDir Path tmp) throws Exception {
        RobotStatus st = new RobotStatus(); st.setBatteryPercent(0.9f);
        assertEquals(90, adapter.readEnergyLevel(st)); // PROTOCOL

        Path f = tmp.resolve("e.json");
        Files.write(f, "{\"energySource\":\"READ_VAR\",\"energyVarName\":\"bat\"}".getBytes(StandardCharsets.UTF_8));
        energyConfig.setConfigFilePath(f);
        energyConfig.reloadFromJsonFile();

        when(mockNavChannel.sendAndGetData(eq(KecongCommandCode.CMD_READ_VAR), any()))
                .thenReturn(readVarResponse("bat", 77.5f));
        assertEquals(78, adapter.readEnergyLevel(null)); // 77.5 rounds to 78
    }
}
