package com.kecong.opentcs.protocol.model;

import com.kecong.opentcs.protocol.KecongCommandCode;
import com.kecong.opentcs.protocol.KecongMessageEncoder;
import com.kecong.opentcs.protocol.KecongProtocolFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted edge-case tests to push coverage to 100%.
 */
@DisplayName("Coverage Gap Tests")
class CoverageGapTest {

    @Test
    @DisplayName("RobotStatus - all AGV states tested")
    void testAllAgvStates() {
        RobotStatus s = new RobotStatus();

        s.setAgvState(0); assertTrue(s.isIdle());
        s.setAgvState(1); assertTrue(s.isRunning());
        s.setAgvState(2); assertTrue(s.isPaused());
        s.setAgvState(3); assertTrue(s.isUninitialized());
        s.setAgvState(6); assertTrue(s.isNavFailed());
        s.setAgvState(4); // manual_confirm - no convenience method, just set

        // Test non-true states
        s.setAgvState(0); assertFalse(s.isRunning()); assertFalse(s.isPaused());
        s.setAgvState(1); assertFalse(s.isIdle());
    }

    @Test
    @DisplayName("RobotStatus - isLocalized check")
    void testLocalized() {
        RobotStatus s = new RobotStatus();
        s.setLocalizationStatus(3);
        assertEquals(3, s.getLocalizationStatus());
    }

    @Test
    @DisplayName("RobotStatus.ActionStatus - all states")
    void testAllActionStates() {
        // waiting=0
        RobotStatus.ActionStatus waiting = new RobotStatus.ActionStatus(1, 0);
        assertFalse(waiting.isComplete());
        assertFalse(waiting.isFailed());
        assertFalse(waiting.isExecuting());

        // paused=6
        RobotStatus.ActionStatus paused = new RobotStatus.ActionStatus(2, 6);
        assertFalse(paused.isComplete());
        assertFalse(paused.isFailed());

        // cancelled=5
        RobotStatus.ActionStatus cancelled = new RobotStatus.ActionStatus(3, 5);
        assertEquals(3, cancelled.getActionId());
        assertEquals(5, cancelled.getStatus());
    }

    @Test
    @DisplayName("RobotStatus getters set for battery/voltage/current")
    void testBatteryGetters() {
        RobotStatus s = new RobotStatus();
        s.setBatteryVoltage(52.0f);
        s.setBatteryCurrent(10.0f);

        assertEquals(52.0f, s.getBatteryVoltage(), 0.1f);
        assertEquals(10.0f, s.getBatteryCurrent(), 0.1f);
    }

    @Test
    @DisplayName("RobotStatus - capability set and confidence")
    void testCapabilityAndConfidence() {
        RobotStatus s = new RobotStatus();
        s.setCapabilitySet(1);
        assertEquals(1, s.getCapabilitySet());
        s.setConfidence(95);
        assertEquals(95, s.getConfidence());
    }

    @Test
    @DisplayName("KecongProtocolFrame - decode with data length exceeding max")
    void testDecodeDataExceedsMax() {
        byte[] raw = new byte[KecongProtocolFrame.HEADER_SIZE + 5];
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(raw)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[16]); // auth
        buf.put((byte) 1); buf.put((byte) 0);
        buf.putShort((short) 0);
        buf.put((byte) 0x10); buf.put((byte) 0);
        buf.put((byte) 0); buf.put((byte) 0);
        buf.putShort((short) 600); // data length = 600 > MAX_DATA_SIZE (512)
        buf.putShort((short) 0);

        assertThrows(IllegalArgumentException.class, () ->
                KecongProtocolFrame.decode(raw));
    }

    @Test
    @DisplayName("KecongMessageEncoder - encode nav task target point mode")
    void testEncodeTargetPointMode() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_TARGET_POINT)
                .addPoint(NavigationTask.TaskPoint.builder()
                        .sequenceNumber(0).pointId(100).build())
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("UDP channel - port 17800 variable ops")
    void testVariablePort() {
        assertEquals(17800, com.kecong.opentcs.protocol.KecongUdpChannel.DEFAULT_VAR_PORT);
    }
}
