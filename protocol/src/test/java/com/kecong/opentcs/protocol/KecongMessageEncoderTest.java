package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.MagneticNavTask;
import com.kecong.opentcs.protocol.model.MagneticNavTask.MagneticControl;
import com.kecong.opentcs.protocol.model.MagneticNavTask.MagneticRelocalize;
import com.kecong.opentcs.protocol.model.NavigationTask;
import com.kecong.opentcs.protocol.model.QrNavigationTask;
import com.kecong.opentcs.protocol.model.TrafficResource;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskAction;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPath;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KecongMessageEncoder.
 */
@DisplayName("KecongMessageEncoder")
class KecongMessageEncoderTest {

    @Test
    @DisplayName("Encode empty request returns empty byte array")
    void testEncodeEmptyRequest() {
        byte[] result = KecongMessageEncoder.encodeEmptyRequest();
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    @DisplayName("Encode navigation task — simple path splice")
    void testEncodeNavigationTaskSimple() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1)
                .taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE)
                .addPoint(TaskPoint.builder().sequenceNumber(0).pointId(5).build())
                .addPoint(TaskPoint.builder().sequenceNumber(2).pointId(120).build())
                .addPoint(TaskPoint.builder().sequenceNumber(4).pointId(119).build())
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);

        // Verify header fields (little-endian)
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, buf.getInt());    // orderId
        assertEquals(1, buf.getInt());    // taskKey
        assertEquals(3, buf.get() & 0xFF); // point count
        assertEquals(0, buf.get() & 0xFF); // path count
        assertEquals(0, buf.get());        // nav mode
    }

    @Test
    @DisplayName("Encode navigation task — path splice with actions")
    void testEncodeNavigationTaskWithActions() {
        TaskAction action = new TaskAction(
                KecongActionType.ACTION_PALLET_LIFT,
                KecongActionType.CONCURRENT_SINGLE,
                1001,
                new byte[]{1, 0, 0, 0});

        NavigationTask task = NavigationTask.builder()
                .orderId(2)
                .taskKey(2)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE)
                .addPoint(TaskPoint.builder().sequenceNumber(0).pointId(10).build())
                .addPoint(TaskPoint.builder().sequenceNumber(2).pointId(20).addAction(action).build())
                .addPath(TaskPath.builder().sequenceNumber(1).pathId(100).travelPose(0).build())
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("Encode navigation task — empty points list")
    void testEncodeNavigationTaskEmpty() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1)
                .taskKey(1)
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertEquals(12, data.length);  // just header: 4+4+1+1+1+1
    }

    @Test
    @DisplayName("Encode immediate action — pause")
    void testEncodeImmediateActionPause() {
        byte[] params = new byte[]{(byte) 0x01, 0, 0, 0, 0x01, 0, 0, 0};  // orderId=1, immediate=true
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                KecongActionType.ACTION_PAUSE,
                KecongActionType.CONCURRENT_ALL,
                42,
                params);

        assertNotNull(data);
        assertTrue(data.length >= 12);
    }

    @Test
    @DisplayName("Encode immediate action — null params")
    void testEncodeImmediateActionNullParams() {
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                KecongActionType.ACTION_CANCEL,
                KecongActionType.CONCURRENT_ALL,
                99,
                null);

        assertNotNull(data);
        assertTrue(data.length >= 12);
    }

    @Test
    @DisplayName("Encode subscription — single command code")
    void testEncodeSubscriptionSingle() {
        byte[] commands = new byte[]{KecongCommandCode.CMD_QUERY_ROBOT_STATUS};
        byte[] data = KecongMessageEncoder.encodeSubscription(commands, 100, 60000, false, "uuid-001");

        assertNotNull(data);
        assertEquals(0x80 + 64, data.length);
    }

    @Test
    @DisplayName("Encode subscription — multiple command codes")
    void testEncodeSubscriptionMultiple() {
        byte[] commands = new byte[]{
                KecongCommandCode.CMD_QUERY_ROBOT_STATUS,
                KecongCommandCode.CMD_QUERY_CARGO_STATUS
        };
        byte[] data = KecongMessageEncoder.encodeSubscription(commands, 50, 30000, true, "uuid-002");

        assertNotNull(data);
        assertEquals(0x80 + 64, data.length);
    }

    @Test
    @DisplayName("Encode subscription — rejects too many command codes")
    void testEncodeSubscriptionTooMany() {
        byte[] commands = new byte[9]; // max is 8
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeSubscription(commands, 100, 60000, false, "uuid"));
    }

    @Test
    @DisplayName("Encode subscription — null command codes")
    void testEncodeSubscriptionNull() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeSubscription(null, 100, 60000, false, "uuid"));
    }

    @Test
    @DisplayName("Encode navigation task — free navigation mode")
    void testEncodeNavigationTaskFreeMode() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1)
                .taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .addPoint(TaskPoint.builder()
                        .sequenceNumber(0)
                        .pointId(42)
                        .angle(1.57f)
                        .specifyAngle(true)
                        .build())
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }
}
