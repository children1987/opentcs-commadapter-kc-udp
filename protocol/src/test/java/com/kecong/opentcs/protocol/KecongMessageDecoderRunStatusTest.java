package com.kecong.opentcs.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.kecong.opentcs.protocol.model.RobotStatus;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for decodeRunStatus (0x17 QUERY_RUN_STATUS response).
 */
@DisplayName("decodeRunStatus")
class KecongMessageDecoderRunStatusTest {

    private static byte[] buildRunStatus(double posX, double posY, double heading,
                                          double battery, int runMode, int locStatus,
                                          int taskState, float confidence) {
        ByteBuffer buf = ByteBuffer.allocate(0xC0);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(0, 35.0);           // body temp
        buf.putDouble(8, posX);           // pos_x
        buf.putDouble(16, posY);          // pos_y
        buf.putDouble(24, heading);       // heading
        buf.putDouble(32, battery);       // battery 0~1
        buf.put(40, (byte) 0);            // blocked
        buf.put(41, (byte) 0);            // charging
        buf.put(42, (byte) runMode);      // run_mode
        buf.put(43, (byte) 0);            // map_loaded
        buf.putInt(44, 0);                // cur_pt
        buf.putDouble(48, 0.1);           // vel_x
        buf.putDouble(56, 0.0);           // ang_vel
        buf.putDouble(64, 48.0);          // bat_voltage
        buf.putDouble(72, 0.0);           // current
        buf.put(80, (byte) taskState);    // task_state
        // skip to 0x70
        buf.put(0x70, (byte) locStatus);  // loc_status
        buf.putFloat(0xB8, confidence);   // confidence
        return buf.array();
    }

    @Test
    @DisplayName("Decodes position in AUTO mode with DONE localization")
    void testAutoModeLocalized() {
        byte[] data = buildRunStatus(1.5, 2.3, 0.5, 0.92, 1, 3, 0, 1.0f);
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(1.5f, st.getPositionX(), 0.01f);
        assertEquals(2.3f, st.getPositionY(), 0.01f);
        assertEquals(0.5f, st.getHeadingAngle(), 0.01f);
        assertEquals(0.92f, st.getBatteryPercent(), 0.01f);
        assertEquals(3, st.getWorkMode());     // AUTO
        assertEquals(3, st.getLocalizationStatus()); // DONE
        assertEquals(0, st.getAgvState());     // IDLE (taskState=0)
        assertEquals(0, st.getNavTaskState());
        assertEquals(100, st.getConfidence());
    }

    @Test
    @DisplayName("Decodes MANUAL mode")
    void testManualMode() {
        byte[] data = buildRunStatus(0, 0, 0, 0.5, 0, 1, 0, 0.95f);
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(1, st.getWorkMode());     // MANUAL
        assertEquals(1, st.getLocalizationStatus()); // SUCCESS
    }

    @Test
    @DisplayName("Decodes GOING task state as RUNNING")
    void testTaskGoing() {
        byte[] data = buildRunStatus(0, 0, 0, 0.8, 1, 3, 2, 1.0f);
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(2, st.getNavTaskState());  // GOING
        assertEquals(1, st.getAgvState());      // RUNNING
    }

    @Test
    @DisplayName("Decodes DONE task state as IDLE")
    void testTaskDone() {
        byte[] data = buildRunStatus(0, 0, 0, 0.8, 1, 3, 4, 1.0f);
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(4, st.getNavTaskState());  // DONE
        assertEquals(0, st.getAgvState());      // IDLE
        assertTrue(st.isNavDone());
    }

    @Test
    @DisplayName("Decodes FAILED task state")
    void testTaskFailed() {
        byte[] data = buildRunStatus(0, 0, 0, 0.8, 1, 3, 5, 1.0f);
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(5, st.getNavTaskState());  // FAILED
        assertEquals(6, st.getAgvState());      // NAV_FAILED
        assertTrue(st.isNavTaskFailed());
    }

    @Test
    @DisplayName("Decodes PAUSED task state")
    void testTaskPaused() {
        byte[] data = buildRunStatus(0, 0, 0, 0.8, 1, 3, 3, 1.0f);
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(3, st.getNavTaskState());  // PAUSED
        assertEquals(2, st.getAgvState());      // PAUSED
    }

    @Test
    @DisplayName("Handles null/empty input gracefully")
    void testNullInput() {
        assertNull(KecongMessageDecoder.decodeRunStatus(null));
        assertNull(KecongMessageDecoder.decodeRunStatus(new byte[0]));
        assertNull(KecongMessageDecoder.decodeRunStatus(new byte[10]));
    }

    @Test
    @DisplayName("Confidence is scaled from float 0-1 to int 0-100")
    void testConfidenceScaling() {
        byte[] data = buildRunStatus(0, 0, 0, 0.8, 1, 3, 0, 0.68f);
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(68, st.getConfidence());
    }

    @Test
    @DisplayName("Battery percentage from DOUBLE")
    void testBattery() {
        byte[] data = buildRunStatus(0, 0, 0, 0.45, 1, 3, 0, 1.0f);
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(0.45f, st.getBatteryPercent(), 0.001f);
    }

    @Test
    @DisplayName("Velocity values decoded")
    void testVelocity() {
        byte[] data = buildRunStatus(0, 0, 0, 0.8, 1, 3, 0, 1.0f);
        // velocity is set in buildRunStatus at offset 48/56
        RobotStatus st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(0.1f, st.getVelocityX(), 0.01f);
        assertEquals(0.0f, st.getAngularVelocity(), 0.01f);
    }
}
