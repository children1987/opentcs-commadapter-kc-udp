package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.NavigationTask;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskAction;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final edge-case tests to push coverage to 100%.
 */
@DisplayName("Coverage Final Push")
class CoverageFinalPushTest {

    @Test
    @DisplayName("Encode nav task - free navigation without specifying angle")
    void testEncodeFreeModeNoAngle() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .addPoint(TaskPoint.builder().sequenceNumber(0).pointId(10)
                        .angle(null).specifyAngle(false).build())
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
        // Verify header
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, buf.getInt()); // orderId
        assertEquals(1, buf.getInt()); // taskKey
        assertEquals(1, buf.get() & 0xFF); // point count
        assertEquals(0, buf.get() & 0xFF); // path count
        assertEquals(NavigationTask.NAV_MODE_FREE, buf.get()); // nav mode
    }

    @Test
    @DisplayName("Encode action with 4-byte aligned params")
    void testEncodeActionAligned() {
        byte[] params = new byte[]{1, 2, 3, 4}; // exactly 4 bytes
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                KecongActionType.ACTION_PAUSE, (byte) 0, 1, params);
        assertNotNull(data);
        // 0x0C header + 4 params + 0 padding (already aligned)
        assertEquals(0x0C + 4, data.length);
    }

    @Test
    @DisplayName("Encode action with params needing alignment")
    void testEncodeActionUnaligned() {
        byte[] params = new byte[]{1, 2, 3}; // 3 bytes, needs 1 byte padding
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                KecongActionType.ACTION_PAUSE, (byte) 0, 1, params);
        assertNotNull(data);
        // 0x0C header + 3 params + 1 padding = 16
        assertEquals(16, data.length);
    }

    @Test
    @DisplayName("Decode robot status - with both events and actions present")
    void testDecodeBothEventsAndActions() {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(256)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1); // abnormalSize=1
        buf.put((byte) 1); // actionSize=1
        buf.putShort((short) 0);
        // Location (32 bytes)
        for (int i = 0; i < 8; i++) buf.putInt(0);
        // Running (20 bytes)
        for (int i = 0; i < 5; i++) buf.putInt(0);
        // Task (12 bytes)
        for (int i = 0; i < 3; i++) buf.putInt(0);
        // Battery (16 bytes)
        for (int i = 0; i < 4; i++) buf.putInt(0);
        // Event
        buf.putShort((short) 0x0108); buf.putShort((short) 2); buf.put(new byte[8]);
        // Action
        buf.putInt(42); buf.put((byte) 3); buf.put(new byte[7]);

        var status = KecongMessageDecoder.decodeRobotStatus(buf.array());
        assertNotNull(status);
        assertNotNull(status.getAbnormalEvents());
        assertEquals(1, status.getAbnormalEvents().length);
        assertNotNull(status.getActionStatuses());
        assertEquals(1, status.getActionStatuses().length);
    }

    @Test
    @DisplayName("KecongProtocolFrame - decode with data length within bounds")
    void testDecodeValidDataLength() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode("AUTH").asResponse().sequenceNumber(0)
                .commandCode((byte) 0xAF).executionCode((byte) 0x00)
                .data(new byte[]{1, 2}).build();

        byte[] encoded = frame.encode();
        KecongProtocolFrame decoded = KecongProtocolFrame.decode(encoded);
        assertEquals(2, decoded.getDataLength());
        assertArrayEquals(new byte[]{1, 2}, decoded.getData());
    }

    // ==== Helpers ====

    private static byte[] buildTightStatusData(byte abnormalSize, byte actionSize) {
        // Build a buffer just big enough for header + location + running + task + battery
        // but NOT big enough for abnormal events or actions
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(4 + 32 + 20 + 12 + 16)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put(abnormalSize);
        buf.put(actionSize);
        buf.putShort((short) 0);
        // Location (32 bytes)
        for (int i = 0; i < 8; i++) buf.putInt(0);
        // Running (20 bytes)
        for (int i = 0; i < 5; i++) buf.putInt(0);
        // Task (12 bytes)
        for (int i = 0; i < 3; i++) buf.putInt(0);
        // Battery (16 bytes)
        for (int i = 0; i < 4; i++) buf.putInt(0);
        return buf.array();
    }
}
