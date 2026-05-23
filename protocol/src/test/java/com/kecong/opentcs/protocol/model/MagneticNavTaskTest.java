package com.kecong.opentcs.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MagneticNavTask")
class MagneticNavTaskTest {

    // === Landmark (12 bytes) ===

    @Test
    @DisplayName("Landmark builder defaults")
    void testLandmarkDefaults() {
        MagneticNavTask.Landmark lm = MagneticNavTask.Landmark.builder(1).build();
        assertEquals(1, lm.getSegmentId());
        assertEquals(1, lm.getSegmentType());
        assertEquals(1, lm.getMotionMode());
        assertFalse(lm.isTrafficMgmtEnabled());
        assertEquals(100, lm.getSegmentLength());
        assertEquals(30, lm.getStraightSpeed());
        assertEquals(0, lm.getAngularVelocity());
        assertEquals(0, lm.getDerailDistance());
    }

    @Test
    @DisplayName("Landmark builder with all fields")
    void testLandmarkAllFields() {
        MagneticNavTask.Landmark lm = MagneticNavTask.Landmark.builder(5)
                .segmentType(2).motionMode(2).trafficMgmtEnabled(true)
                .segmentLength(200).straightSpeed(50).angularVelocity(30).derailDistance(5)
                .build();
        assertEquals(5, lm.getSegmentId());
        assertEquals(2, lm.getSegmentType());
        assertEquals(2, lm.getMotionMode());
        assertTrue(lm.isTrafficMgmtEnabled());
        assertEquals(200, lm.getSegmentLength());
        assertEquals(50, lm.getStraightSpeed());
        assertEquals(30, lm.getAngularVelocity());
        assertEquals(5, lm.getDerailDistance());
    }

    @Test
    @DisplayName("Landmark encode 12 bytes")
    void testLandmarkEncode() {
        MagneticNavTask.Landmark lm = MagneticNavTask.Landmark.builder(3)
                .segmentType(2).motionMode(2).trafficMgmtEnabled(true)
                .segmentLength(150).straightSpeed(40).build();

        ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        lm.encodeTo(buf);
        assertEquals(12, buf.position());

        buf.flip();
        assertEquals(3, buf.getShort() & 0xFFFF);
        assertEquals(0x22, buf.get() & 0xFF);      // segType=2 (high nibble), motionMode=2 (low nibble)
        assertEquals(1, buf.get() & 0xFF);           // traffic mgmt
        assertEquals(150, buf.getShort() & 0xFFFF);  // length
        assertEquals(40, buf.getShort() & 0xFFFF);   // speed
        assertEquals(0, buf.getShort() & 0xFFFF);    // angular vel
        assertEquals(0, buf.getShort() & 0xFFFF);    // derail
    }

    // === Tagmark (8 bytes) ===

    @Test
    @DisplayName("Tagmark encode 8 bytes")
    void testTagmark() {
        MagneticNavTask.Tagmark tm = new MagneticNavTask.Tagmark(10, 12345);
        assertEquals(10, tm.getOwningSegmentId());
        assertEquals(12345, tm.getLandmarkId());

        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        tm.encodeTo(buf);
        assertEquals(8, buf.position());

        buf.flip();
        assertEquals(10, buf.getShort() & 0xFFFF);
        assertEquals(0, buf.get() & 0xFF);       // reserved
        assertEquals(0, buf.get() & 0xFF);       // padding
        assertEquals(12345, buf.getInt());       // landmark ID
    }

    // === 0xE0 Task Encoding (header=16 bytes) ===

    @Test
    @DisplayName("Encode 0xE0 task with landmarks and tagmarks")
    void testEncodeE0() {
        MagneticNavTask task = MagneticNavTask.builder()
                .totalSegmentCount(2).totalTagmarkCount(1).stopDistance(10)
                .addLandmark(MagneticNavTask.Landmark.builder(1).segmentLength(200).build())
                .addLandmark(MagneticNavTask.Landmark.builder(2).segmentLength(300).build())
                .addTagmark(new MagneticNavTask.Tagmark(1, 100))
                .build();

        byte[] data = task.toE0Bytes();
        assertEquals(16 + 2 * 12 + 1 * 8, data.length);

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(2, buf.getShort() & 0xFFFF);
        assertEquals(1, buf.getShort() & 0xFFFF);
        assertEquals(10f, buf.getFloat(), 0.01f);
    }

    @Test
    @DisplayName("Encode 0xE0 empty task")
    void testEncodeE0Empty() {
        MagneticNavTask task = MagneticNavTask.builder().build();
        byte[] data = task.toE0Bytes();
        assertEquals(16, data.length);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, buf.getShort() & 0xFFFF);
        assertEquals(0, buf.getShort() & 0xFFFF);
        assertEquals(0f, buf.getFloat(), 0.01f);
    }

    // === 0xE1 Control ===

    @Test
    @DisplayName("0xE1 control constants and encoding")
    void testE1Control() {
        assertEquals(0, MagneticNavTask.MagneticControl.CTRL_PAUSE);
        assertEquals(1, MagneticNavTask.MagneticControl.CTRL_RESUME);
        assertEquals(2, MagneticNavTask.MagneticControl.CTRL_CANCEL);
        assertEquals(3, MagneticNavTask.MagneticControl.CTRL_START);
        assertEquals(6, MagneticNavTask.MagneticControl.CTRL_CLEAR_FAULT);

        MagneticNavTask.MagneticControl ctrl = new MagneticNavTask.MagneticControl(3, true);
        byte[] data = ctrl.toBytes();
        assertEquals(4, data.length);
        assertEquals(3, data[0] & 0xFF);
        assertEquals(1, data[1] & 0xFF);
    }

    // === 0xE2 Status (20 bytes) ===

    @Test
    @DisplayName("Decode 0xE2 status — running and located")
    void testDecodeE2Running() {
        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0);  // run: normal
        buf.put((byte) 0);  // reserved
        buf.put((byte) 2);  // nav: executing
        buf.put((byte) 1);  // landmark detected
        buf.putShort((short) 42); // segment ID
        buf.put((byte) 0);  // nav allowed
        buf.put((byte) 0);  // no traffic wait
        buf.putFloat(150.5f);  // position from start
        buf.putShort((short) 90); // heading 90
        buf.put((byte) 1);   // heading valid
        buf.put((byte) 0);   // positioning: success
        buf.putShort((short) 0); // waiting seg ID
        buf.putShort((short) 0); // path start ID

        MagneticNavTask.MagneticStatus s = MagneticNavTask.fromE2Response(buf.array());
        assertNotNull(s);
        assertEquals(0, s.getRunStatus());
        assertEquals(2, s.getNavStatus());
        assertTrue(s.isLandmarkDetected());
        assertEquals(42, s.getCurrentSegmentId());
        assertEquals(150.5f, s.getPositionX(), 0.01f);
        assertEquals(90, s.getHeadingAngle());
        assertTrue(s.isLocated());
        assertFalse(s.isTrafficControlWait());
    }

    @Test
    @DisplayName("Decode 0xE2 status — derail fault")
    void testDecodeE2Fault() {
        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 2);  // derail fault
        buf.put((byte) 0);
        buf.put((byte) 2);
        buf.put((byte) 0);
        buf.putShort((short) 0);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.putFloat(0);
        buf.putShort((short) -45);
        buf.put((byte) 1);
        buf.put((byte) 1);  // need reloc
        buf.putShort((short) 0);
        buf.putShort((short) 0);

        MagneticNavTask.MagneticStatus s = MagneticNavTask.fromE2Response(buf.array());
        assertTrue(s.isDerailed());
        assertFalse(s.isLocated());
    }

    @Test
    @DisplayName("Decode 0xE2 null/empty")
    void testDecodeE2Null() {
        assertNull(MagneticNavTask.fromE2Response(null));
        assertNull(MagneticNavTask.fromE2Response(new byte[5]));
    }

    // === 0xE3 Relocalize (12 bytes, float distance) ===

    @Test
    @DisplayName("0xE3 relocalize magnetic tape")
    void testRelocalizeTape() {
        MagneticNavTask.MagneticRelocalize reloc = new MagneticNavTask.MagneticRelocalize(
                5, (short) 90, 100.5f, 200,
                MagneticNavTask.MagneticRelocalize.SEG_TYPE_MAGNETIC_TAPE);

        assertEquals(5, reloc.getSegmentId());
        assertEquals(90, reloc.getHeadingAngle());
        assertEquals(100.5f, reloc.getDistanceFromStart(), 0.01f);
        assertEquals(200, reloc.getStartPointId());
        assertEquals(0, reloc.getSegmentType());

        byte[] data = reloc.toBytes();
        assertEquals(12, data.length);

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(5, buf.getShort() & 0xFFFF);
        assertEquals(90, buf.getShort());
        assertEquals(100.5f, buf.getFloat(), 0.01f);
        assertEquals(200, buf.getShort() & 0xFFFF);
        assertEquals(0, buf.get() & 0xFF);
    }

    @Test
    @DisplayName("0xE3 relocalize magnetic nail")
    void testRelocalizeNail() {
        MagneticNavTask.MagneticRelocalize reloc = new MagneticNavTask.MagneticRelocalize(
                3, (short) -180, 0f, 1, 1);
        assertEquals(1, reloc.getSegmentType());
        byte[] data = reloc.toBytes();
        assertEquals(1, data[10] & 0xFF);
    }

    // === Immutability ===

    @Test
    @DisplayName("Lists are unmodifiable")
    void testImmutability() {
        MagneticNavTask task = MagneticNavTask.builder()
                .addLandmark(MagneticNavTask.Landmark.builder(1).build())
                .addTagmark(new MagneticNavTask.Tagmark(1, 10))
                .build();
        assertThrows(UnsupportedOperationException.class, () ->
                task.getLandmarks().add(MagneticNavTask.Landmark.builder(2).build()));
        assertThrows(UnsupportedOperationException.class, () ->
                task.getTagmarks().add(new MagneticNavTask.Tagmark(2, 20)));
    }

    @Test
    @DisplayName("toString coverage")
    void testToStrings() {
        MagneticNavTask task = MagneticNavTask.builder()
                .totalSegmentCount(3).totalTagmarkCount(2).stopDistance(15)
                .addLandmark(MagneticNavTask.Landmark.builder(1).build())
                .addTagmark(new MagneticNavTask.Tagmark(1, 100))
                .build();
        assertTrue(task.toString().contains("3"));
        assertTrue(task.toString().contains("15"));

        MagneticNavTask.MagneticControl ctrl = new MagneticNavTask.MagneticControl(3, true);
        assertTrue(ctrl.toString().contains("3"));

        MagneticNavTask.MagneticRelocalize reloc = new MagneticNavTask.MagneticRelocalize(1, (short) 0, 0f, 0, 0);
        assertTrue(reloc.toString().contains("tape"));

        // Status toString
        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) buf.put((byte) 0);
        buf.flip();
        MagneticNavTask.MagneticStatus s = MagneticNavTask.fromE2Response(buf.array());
        assertNotNull(s);
        assertTrue(s.toString().contains("MagneticStatus"));
    }
}
