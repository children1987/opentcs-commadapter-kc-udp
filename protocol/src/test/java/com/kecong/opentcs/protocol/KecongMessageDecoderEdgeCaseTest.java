package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.RobotStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for KecongMessageDecoder to push coverage to 100%.
 */
@DisplayName("KecongMessageDecoder Edge Cases")
class KecongMessageDecoderEdgeCaseTest {

    @Test
    @DisplayName("Decode robot status with charge full state")
    void testDecodeChargeFull() {
        byte[] data = buildBaseStatus();
        // Set charge status to full (0x02) at byte offset 80
        data[80] = (byte) 0x02;

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertEquals(2, status.getChargeStatus());
        assertTrue(status.isFull());
        assertFalse(status.isCharging());
    }

    @Test
    @DisplayName("Decode robot status running state")
    void testDecodeRunningState() {
        byte[] data = buildBaseStatus();
        // Set agvState to 1 (running)
        data[0x24 + 4 + 4 + 4 + 1] = (byte) 0x01;

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertTrue(status.isRunning());
        assertEquals(1, status.getAgvState());
    }

    @Test
    @DisplayName("Decode robot status paused state")
    void testDecodePausedState() {
        byte[] data = buildBaseStatus();
        data[0x24 + 4 + 4 + 4 + 1] = (byte) 0x02;

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertTrue(status.isPaused());
    }

    @Test
    @DisplayName("Decode robot status uninitialized state")
    void testDecodeUninitialized() {
        byte[] data = buildBaseStatus();
        data[0x24 + 4 + 4 + 4 + 1] = (byte) 0x03;

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertTrue(status.isUninitialized());
    }

    @Test
    @DisplayName("Decode robot status with abnormal event info level")
    void testDecodeAbnormalEventInfo() {
        byte[] data = buildBaseStatus();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(0, (byte) 1); // abnormalSize=1
        buf.put(1, (byte) 0); // actionSize=0
        buf.position(88);
        buf.putShort((short) 0x0005);
        buf.putShort((short) 0); // level=info
        buf.put(new byte[8]);

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertNotNull(status.getAbnormalEvents());
        assertEquals(1, status.getAbnormalEvents().length);
        assertFalse(status.getAbnormalEvents()[0].isError());
        assertFalse(status.getAbnormalEvents()[0].isWarning());
        assertFalse(status.hasError());
    }

    @Test
    @DisplayName("Decode robot status with action executing")
    void testDecodeActionExecuting() {
        byte[] data = buildBaseStatus();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(0, (byte) 0); // abnormalSize=0
        buf.put(1, (byte) 1); // actionSize=1
        buf.position(88);
        buf.putInt(200);
        buf.put((byte) 2); // executing
        buf.put(new byte[7]);

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertNotNull(status.getActionStatuses());
        assertEquals(1, status.getActionStatuses().length);
        assertTrue(status.getActionStatuses()[0].isExecuting());
        assertEquals(200, status.getActionStatuses()[0].getActionId());
    }

    @Test
    @DisplayName("Decode robot status with action failed")
    void testDecodeActionFailed() {
        byte[] data = buildBaseStatus();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(0, (byte) 0);
        buf.put(1, (byte) 1);
        buf.position(88);
        buf.putInt(300);
        buf.put((byte) 4); // failed
        buf.put(new byte[7]);

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertTrue(status.getActionStatuses()[0].isFailed());
    }

    @Test
    @DisplayName("Decode robot status with no errors and no actions (remain zero)")
    void testDecodeNoDataForEventsOrActions() {
        byte[] data = buildBaseStatus();
        // abnormalSize=0, actionSize=0 (defaults)
        // Point/path sizes also 0

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertFalse(status.hasError());
        // actionStatuses can be null when actionSize=0
    }

    // ==== Helper ====

    private static byte[] buildBaseStatus() {
        ByteBuffer buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0); buf.put((byte) 0); buf.putShort((short) 0);
        // Location
        buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f);
        buf.putInt(0); buf.putInt(0); buf.putInt(0);
        buf.put((byte) 0); buf.put((byte) 0); buf.put(new byte[6]);
        // Running
        buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f);
        buf.put((byte) 0); buf.put((byte) 0); buf.put((byte) 0); buf.put(new byte[5]);
        // Task
        buf.putInt(0); buf.putInt(0);
        buf.put((byte) 0); buf.put((byte) 0); buf.putShort((short) 0);
        // Battery
        buf.putFloat(0f); buf.putFloat(0f); buf.putFloat(0f);
        buf.put((byte) 0); buf.put(new byte[7]);
        return buf.array();
    }
}
