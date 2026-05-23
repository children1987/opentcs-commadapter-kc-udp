package com.kecong.opentcs.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QrNavigationTask")
class QrNavigationTaskTest {

    // === Segment Builder ===

    @Test
    @DisplayName("Segment builder creates valid segment with defaults")
    void testSegmentDefaults() {
        QrNavigationTask.Segment seg = new QrNavigationTask.Segment.Builder(10, 20).build();
        assertEquals(10, seg.getDestinationQrLabelId());
        assertEquals(20, seg.getRouteSegmentId());
        assertEquals(0, seg.getDx());
        assertEquals(0, seg.getDy());
        assertEquals(0, seg.getDestHeadingAngle());
        assertFalse(seg.isStopRequired());
        assertFalse(seg.isLaserShieldEnabled());
        assertEquals(30, seg.getLinearSpeed()); // default
        assertEquals(0, seg.getRotationLimit());
    }

    @Test
    @DisplayName("Segment builder with all fields set")
    void testSegmentAllFields() {
        QrNavigationTask.Segment seg = new QrNavigationTask.Segment.Builder(5, 6)
                .dx((short) 1000)
                .dy((short) -500)
                .destHeadingAngle(1800) // 180.0 degrees
                .stopRequired(true)
                .laserShieldEnabled(true)
                .linearSpeed(50)
                .rotationLimit(1) // clockwise
                .build();

        assertEquals(5, seg.getDestinationQrLabelId());
        assertEquals(6, seg.getRouteSegmentId());
        assertEquals(1000, seg.getDx());
        assertEquals(-500, seg.getDy());
        assertEquals(1800, seg.getDestHeadingAngle());
        assertTrue(seg.isStopRequired());
        assertTrue(seg.isLaserShieldEnabled());
        assertEquals(50, seg.getLinearSpeed());
        assertEquals(1, seg.getRotationLimit());
    }

    // === 0xF1 Encoding ===

    @Test
    @DisplayName("Encode 0xF1 simple task with one segment")
    void testEncodeF1SingleSegment() {
        QrNavigationTask task = QrNavigationTask.builder()
                .angleLimitEnabled(true)
                .angleLimit(45)
                .addSegment(new QrNavigationTask.Segment.Builder(100, 200)
                        .dx((short) 3000)
                        .dy((short) 0)
                        .destHeadingAngle(900) // 90.0 degrees
                        .linearSpeed(40)
                        .build())
                .build();

        byte[] data = task.toF1Bytes();
        assertNotNull(data);
        assertEquals(8 + 16, data.length); // header(8) + 1 segment(16)

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, buf.getShort() & 0xFFFF); // segment count
        assertEquals(1, buf.get() & 0xFF);  // angleLimitEnabled
        assertEquals(45, buf.get() & 0xFF); // angleLimit
        buf.getShort(); // reserved
        buf.getShort(); // reserved

        // Segment
        assertEquals(100, buf.getShort() & 0xFFFF);
        assertEquals(200, buf.getShort() & 0xFFFF);
        assertEquals(3000, buf.getShort());
        assertEquals(0, buf.getShort());
        assertEquals(900, buf.getShort() & 0xFFFF);
        assertEquals(0, buf.get() & 0xFF);  // no stop
        assertEquals(0, buf.get() & 0xFF);  // no laser shield
        assertEquals(40, buf.getShort() & 0xFFFF);
        assertEquals(0, buf.get() & 0xFF);  // bidir
        assertEquals(0, buf.get() & 0xFF);  // reserved
    }

    @Test
    @DisplayName("Encode 0xF1 with multiple segments")
    void testEncodeF1MultipleSegments() {
        QrNavigationTask task = QrNavigationTask.builder()
                .addSegment(new QrNavigationTask.Segment.Builder(1, 11).build())
                .addSegment(new QrNavigationTask.Segment.Builder(2, 22).stopRequired(true).build())
                .addSegment(new QrNavigationTask.Segment.Builder(3, 33).build())
                .build();

        byte[] data = task.toF1Bytes();
        assertEquals(8 + 3 * 16, data.length);

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3, buf.getShort() & 0xFFFF); // 3 segments
    }

    @Test
    @DisplayName("Segment builder exceeds MAX_SEGMENTS_PER_BATCH")
    void testSegmentLimit() {
        QrNavigationTask.Builder builder = QrNavigationTask.builder();
        for (int i = 0; i < 30; i++) {
            builder.addSegment(new QrNavigationTask.Segment.Builder(i, i * 10).build());
        }
        assertThrows(IllegalStateException.class, () ->
                builder.addSegment(new QrNavigationTask.Segment.Builder(31, 310).build()));
    }

    // === 0xF5 Encoding ===

    @Test
    @DisplayName("Encode 0xF5 long-path task with offset")
    void testEncodeF5WithOffset() {
        QrNavigationTask task = QrNavigationTask.builder()
                .totalSegmentCount(120)
                .segmentOffset(60)
                .angleLimitEnabled(true)
                .angleLimit(30)
                .addSegment(new QrNavigationTask.Segment.Builder(10, 100)
                        .linearSpeed(35)
                        .build())
                .addSegment(new QrNavigationTask.Segment.Builder(20, 200)
                        .destHeadingAngle(1800)
                        .rotationLimit(2) // CCW
                        .build())
                .build();

        byte[] data = task.toF5Bytes();
        assertEquals(16 + 2 * 16, data.length);

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(120, buf.getShort() & 0xFFFF); // total count
        assertEquals(1, buf.get() & 0xFF);   // angle limit enabled
        assertEquals(30, buf.get() & 0xFF);  // angle limit
        buf.position(buf.position() + 8);    // skip reserved[8]
        assertEquals(60, buf.getShort() & 0xFFFF); // offset
        assertEquals(2, buf.getShort() & 0xFFFF);  // batch count

        // Segment 1
        assertEquals(10, buf.getShort() & 0xFFFF);  // destinationQrLabelId
        assertEquals(100, buf.getShort() & 0xFFFF); // routeSegmentId
        buf.position(buf.position() + 4);            // dx + dy
        buf.position(buf.position() + 4);            // headingAngle + stopFlag + laserShield (2+1+1)
        assertEquals(35, buf.getShort() & 0xFFFF);  // linearSpeed at offset 0CH

        // Segment 2: seek to heading field
        buf.position(16 + 16 + 8); // header(16) + seg1(16) + skip to heading at +8
        assertEquals(1800, buf.getShort() & 0xFFFF); // destHeadingAngle
    }

    // === 0xF5 Response Decoding ===

    @Test
    @DisplayName("Decode 0xF5 response valid")
    void testDecodeF5Response() {
        ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 500); // total segments
        buf.put((byte) 1);        // angle limit enabled
        buf.put((byte) 60);       // angle limit 60 degrees
        buf.put(new byte[8]);     // reserved

        QrNavigationTask r = QrNavigationTask.fromF5Response(buf.array());
        assertNotNull(r);
        assertEquals(500, r.getTotalSegmentCount());
        assertTrue(r.isAngleLimitEnabled());
        assertEquals(60, r.getAngleLimit());
    }

    @Test
    @DisplayName("Decode 0xF5 response angle limit disabled")
    void testDecodeF5ResponseNoAngleLimit() {
        ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 10);
        buf.put((byte) 0);  // disabled
        buf.put((byte) 0);
        buf.put(new byte[8]);

        QrNavigationTask r = QrNavigationTask.fromF5Response(buf.array());
        assertFalse(r.isAngleLimitEnabled());
    }

    @Test
    @DisplayName("Decode 0xF5 null/empty returns null")
    void testDecodeF5ResponseNull() {
        assertNull(QrNavigationTask.fromF5Response(null));
        assertNull(QrNavigationTask.fromF5Response(new byte[2]));
    }

    // === Segments list is immutable ===

    @Test
    @DisplayName("Segments list is unmodifiable")
    void testSegmentsUnmodifiable() {
        QrNavigationTask task = QrNavigationTask.builder()
                .addSegment(new QrNavigationTask.Segment.Builder(1, 10).build())
                .build();
        assertThrows(UnsupportedOperationException.class, () ->
                task.getSegments().add(new QrNavigationTask.Segment.Builder(2, 20).build()));
    }

    @Test
    @DisplayName("toString contains key fields")
    void testToString() {
        QrNavigationTask task = QrNavigationTask.builder()
                .totalSegmentCount(5)
                .angleLimitEnabled(true)
                .angleLimit(45)
                .addSegment(new QrNavigationTask.Segment.Builder(1, 10).build())
                .build();
        String s = task.toString();
        assertTrue(s.contains("5"));
        assertTrue(s.contains("45"));
    }
}
