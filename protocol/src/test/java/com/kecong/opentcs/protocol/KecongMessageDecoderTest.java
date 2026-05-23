package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.RobotStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KecongMessageDecoder — robot status and cargo status decoding.
 */
@DisplayName("KecongMessageDecoder")
class KecongMessageDecoderTest {

    @Test
    @DisplayName("Decode robot status — null/empty data")
    void testDecodeRobotStatusNull() {
        assertNull(KecongMessageDecoder.decodeRobotStatus(null));
        assertNull(KecongMessageDecoder.decodeRobotStatus(new byte[0]));
    }

    @Test
    @DisplayName("Decode robot status — valid data")
    void testDecodeRobotStatusValid() {
        byte[] data = buildMinimalRobotStatusData();
        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);

        assertEquals(1.5f, status.getPositionX(), 0.01f);
        assertEquals(2.5f, status.getPositionY(), 0.01f);
        assertEquals(3.14f, status.getHeadingAngle(), 0.01f);
        assertEquals(5, status.getLastPassedPointId());
        assertEquals(100, status.getLastPassedPathId());
        assertEquals(0, status.getPointSequenceNumber());
        assertEquals(90, status.getConfidence());
        assertEquals(3, status.getLocalizationStatus());

        assertEquals(0.5f, status.getVelocityX(), 0.01f);
        assertEquals(0f, status.getVelocityY(), 0.01f);
        assertEquals(0.1f, status.getAngularVelocity(), 0.01f);
        assertEquals(3, status.getWorkMode());
        assertTrue(status.isAutoMode());
        assertEquals(0, status.getAgvState());
        assertTrue(status.isIdle());

        assertEquals(1, status.getOrderId());
        assertEquals(1, status.getTaskKey());
        assertTrue(status.hasTask());

        assertEquals(0.85f, status.getBatteryPercent(), 0.01f);
        assertEquals(48.0f, status.getBatteryVoltage(), 0.1f);
        assertEquals(5.0f, status.getBatteryCurrent(), 0.1f);
        assertEquals(0, status.getChargeStatus());

        // No errors, no actions
        assertFalse(status.hasError());
    }

    @Test
    @DisplayName("Decode robot status — with error events")
    void testDecodeRobotStatusWithErrors() {
        byte[] data = buildRobotStatusWithErrors();
        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertTrue(status.hasError());

        assertNotNull(status.getAbnormalEvents());
        assertEquals(2, status.getAbnormalEvents().length);
        assertEquals(0x0108, status.getAbnormalEvents()[0].getEventCode());
        assertEquals(2, status.getAbnormalEvents()[0].getLevel());
        assertTrue(status.getAbnormalEvents()[0].isError());
        assertEquals(0x0113, status.getAbnormalEvents()[1].getEventCode());
        assertEquals(1, status.getAbnormalEvents()[1].getLevel());
        assertTrue(status.getAbnormalEvents()[1].isWarning());
    }

    @Test
    @DisplayName("Decode robot status — with action status")
    void testDecodeRobotStatusWithActions() {
        byte[] data = buildRobotStatusWithActions();
        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);

        assertNotNull(status.getActionStatuses());
        assertEquals(1, status.getActionStatuses().length);
        assertEquals(1001, status.getActionStatuses()[0].getActionId());
        assertEquals(3, status.getActionStatuses()[0].getStatus());
        assertTrue(status.getActionStatuses()[0].isComplete());
    }

    @Test
    @DisplayName("Decode robot status — no task")
    void testDecodeRobotStatusNoTask() {
        byte[] data = buildMinimalRobotStatusData();
        // Modify orderId to 0
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.position(0x38);  // TaskStatusInfo starts
        buf.putInt(0);       // orderId = 0

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertFalse(status.hasTask());
        assertEquals(0, status.getOrderId());
    }

    @Test
    @DisplayName("Decode robot status — nav failed state")
    void testDecodeRobotStatusNavFailed() {
        byte[] data = buildMinimalRobotStatusData();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.position(0x24 + 4 + 4 + 4 + 1); // agvState byte in RunningStatusInfo
        buf.put((byte) 6);  // nav_failed

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertNotNull(status);
        assertTrue(status.isNavFailed());
    }

    @Test
    @DisplayName("Decode cargo status — loaded")
    void testDecodeCargoStatusLoaded() {
        assertTrue(KecongMessageDecoder.decodeCargoStatus(new byte[]{0x01, 0, 0, 0}));
    }

    @Test
    @DisplayName("Decode cargo status — unloaded")
    void testDecodeCargoStatusUnloaded() {
        assertFalse(KecongMessageDecoder.decodeCargoStatus(new byte[]{0x00, 0, 0, 0}));
    }

    @Test
    @DisplayName("Decode cargo status — null/empty")
    void testDecodeCargoStatusNull() {
        assertFalse(KecongMessageDecoder.decodeCargoStatus(null));
        assertFalse(KecongMessageDecoder.decodeCargoStatus(new byte[0]));
    }

    @Test
    @DisplayName("Decode robot status — manual mode")
    void testDecodeRobotStatusManualMode() {
        byte[] data = buildMinimalRobotStatusData();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.position(0x24 + 4 + 4 + 4); // workMode byte
        buf.put((byte) 1);  // manual

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertTrue(status.isManualMode());
        assertFalse(status.isAutoMode());
    }

    @Test
    @DisplayName("Decode robot status — charging state")
    void testDecodeRobotStatusCharging() {
        byte[] data = buildMinimalRobotStatusData();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        // BatteryStatusInfo starts after TaskStatusInfo (variable)
        // offset after TaskStatusInfo header (0x38 + 4+4+1+1+2 + pointSize*8 + pathSize*8)
        // For this test, pointSize=0, pathSize=0, so battery starts at 0x38+12 = 0x44
        buf.position(0x44 + 4 + 4 + 4); // chargeStatus
        buf.put((byte) 1);  // charging

        RobotStatus status = KecongMessageDecoder.decodeRobotStatus(data);
        assertTrue(status.isCharging());
        assertFalse(status.isFull());
    }

    // ---- Helper methods to build test data ----

    /**
     * Build minimal valid robot status data matching 0xAF response format.
     */
    private static byte[] buildMinimalRobotStatusData() {
        ByteBuffer buf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);

        // abnormal_size=0, action_size=0, reserved[2]
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.putShort((short) 0);

        // LocationStatusInfo (0x04 to 0x23)
        buf.putFloat(1.5f);       // x
        buf.putFloat(2.5f);       // y
        buf.putFloat(3.14f);      // heading
        buf.putInt(5);             // lastPassedPointId
        buf.putInt(100);           // lastPassedPathId
        buf.putInt(0);             // pointSeqNum
        buf.put((byte) 90);        // confidence
        buf.put((byte) 3);         // localizationStatus=done
        buf.put(new byte[6]);      // reserved

        // RunningStatusInfo (0x24 to 0x37)
        buf.putFloat(0.5f);        // vx
        buf.putFloat(0f);          // vy
        buf.putFloat(0.1f);       // angular vel
        buf.put((byte) 3);         // workMode=auto
        buf.put((byte) 0);         // agvState=idle
        buf.put((byte) 0);         // capabilitySet
        buf.put(new byte[5]);      // reserved

        // TaskStatusInfo (0x38 to ...)
        buf.putInt(1);             // orderId
        buf.putInt(1);             // taskKey
        buf.put((byte) 0);         // pointSize
        buf.put((byte) 0);         // pathSize
        buf.putShort((short) 0);   // reserved

        // BatteryStatusInfo
        buf.putFloat(0.85f);       // battery percent
        buf.putFloat(48.0f);       // voltage
        buf.putFloat(5.0f);        // current
        buf.put((byte) 0);         // chargeStatus=discharging
        buf.put(new byte[7]);      // reserved

        return buf.array();
    }

    private static byte[] buildRobotStatusWithErrors() {
        byte[] base = buildMinimalRobotStatusData();
        ByteBuffer buf = ByteBuffer.wrap(base).order(ByteOrder.LITTLE_ENDIAN);

        // Set abnormal_size to 2
        buf.put(0, (byte) 2);

        // Battery section ends at offset 88 (12 header + 32 location + 20 running + 12 task + 0 skip + 12 battery = 88)
        // Place abnormal events after battery (offset 88)
        buf.position(88);

        // Event 1: error
        buf.putShort((short) 0x0108);
        buf.putShort((short) 2);
        buf.put(new byte[8]);

        // Event 2: warning
        buf.putShort((short) 0x0113);
        buf.putShort((short) 1);
        buf.put(new byte[8]);

        return buf.array();
    }

    private static byte[] buildRobotStatusWithActions() {
        byte[] base = buildMinimalRobotStatusData();
        ByteBuffer buf = ByteBuffer.wrap(base).order(ByteOrder.LITTLE_ENDIAN);

        // Set action_size to 1, abnormal_size stays 0
        buf.put(1, (byte) 1);

        // Action data goes after battery section (offset 88)
        buf.position(88);
        buf.putInt(1001);
        buf.put((byte) 3);
        buf.put(new byte[7]);

        return buf.array();
    }
}
