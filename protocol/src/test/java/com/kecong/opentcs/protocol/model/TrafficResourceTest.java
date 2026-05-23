package com.kecong.opentcs.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TrafficResource")
class TrafficResourceTest {

    // === Decoder: fromQueryResponse (0x70) ===

    @Test
    @DisplayName("Decode null/empty data returns empty resource")
    void testDecodeNullEmpty() {
        TrafficResource r = TrafficResource.fromQueryResponse(null);
        assertFalse(r.hasRequest());
        assertEquals(0, r.getPathCount());

        r = TrafficResource.fromQueryResponse(new byte[0]);
        assertFalse(r.hasRequest());
        assertEquals(0, r.getPathCount());

        r = TrafficResource.fromQueryResponse(new byte[2]); // too short
        assertFalse(r.hasRequest());
        assertEquals(0, r.getPathCount());
    }

    @Test
    @DisplayName("Decode no pending request")
    void testDecodeNoRequest() {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0);  // no request
        buf.put((byte) 0);  // 0 paths
        buf.putShort((short) 0);

        TrafficResource r = TrafficResource.fromQueryResponse(buf.array());
        assertFalse(r.hasRequest());
        assertEquals(0, r.getPathCount());
    }

    @Test
    @DisplayName("Decode pending request with 3 paths")
    void testDecodeWithPaths() {
        ByteBuffer buf = ByteBuffer.allocate(4 + 3 * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);  // has request
        buf.put((byte) 3);  // 3 paths
        buf.putShort((short) 0);
        buf.putShort((short) 10); buf.putShort((short) 100);  // path 10 -> 100
        buf.putShort((short) 20); buf.putShort((short) 200);
        buf.putShort((short) 30); buf.putShort((short) 300);

        TrafficResource r = TrafficResource.fromQueryResponse(buf.array());
        assertTrue(r.hasRequest());
        assertEquals(3, r.getPathCount());

        List<TrafficResource.Path> paths = r.getPaths();
        assertEquals(10, paths.get(0).getPathId());
        assertEquals(100, paths.get(0).getEndpointId());
        assertEquals(20, paths.get(1).getPathId());
        assertEquals(200, paths.get(1).getEndpointId());
        assertEquals(30, paths.get(2).getPathId());
        assertEquals(300, paths.get(2).getEndpointId());
    }

    @Test
    @DisplayName("Decode caps at MAX_PATHS (16)")
    void testDecodeMaxPaths() {
        ByteBuffer buf = ByteBuffer.allocate(4 + 20 * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);
        buf.put((byte) 20); // requests 20, but cap at 16
        buf.putShort((short) 0);
        for (int i = 0; i < 20; i++) {
            buf.putShort((short) i);
            buf.putShort((short) (i * 10));
        }

        TrafficResource r = TrafficResource.fromQueryResponse(buf.array());
        assertEquals(16, r.getPathCount()); // capped
    }

    // === Encoder: toNotifyBytes (0x71) ===

    @Test
    @DisplayName("Encode notify result — success with paths")
    void testEncodeSuccess() {
        TrafficResource r = TrafficResource.newResult(true)
                .addPath(5, 50)
                .addPath(6, 60)
                .build();

        assertTrue(r.isSuccess());
        assertEquals(2, r.getPathCount());

        byte[] data = r.toNotifyBytes();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, buf.get() & 0xFF);  // success
        assertEquals(2, buf.get() & 0xFF);  // path count
        buf.getShort(); // reserved
        assertEquals(5, buf.getShort() & 0xFFFF);
        assertEquals(50, buf.getShort() & 0xFFFF);
        assertEquals(6, buf.getShort() & 0xFFFF);
        assertEquals(60, buf.getShort() & 0xFFFF);
    }

    @Test
    @DisplayName("Encode notify result — failure with no paths")
    void testEncodeFailure() {
        TrafficResource r = TrafficResource.newResult(false).build();
        assertFalse(r.isSuccess());
        assertEquals(0, r.getPathCount());

        byte[] data = r.toNotifyBytes();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, buf.get() & 0xFF);  // failure
        assertEquals(0, buf.get() & 0xFF);  // 0 paths
    }

    @Test
    @DisplayName("Builder throws when exceeding MAX_PATHS")
    void testBuilderMaxPaths() {
        TrafficResource.Builder builder = TrafficResource.newResult(true);
        for (int i = 0; i < 16; i++) {
            builder.addPath(i, i * 10);
        }
        assertThrows(IllegalStateException.class, () -> builder.addPath(17, 170));
    }

    @Test
    @DisplayName("Paths list is unmodifiable")
    void testPathsUnmodifiable() {
        TrafficResource r = TrafficResource.newResult(true).addPath(1, 10).build();
        assertThrows(UnsupportedOperationException.class, () -> r.getPaths().add(new TrafficResource.Path(2, 20)));
    }

    @Test
    @DisplayName("toString contains key fields")
    void testToString() {
        TrafficResource r = TrafficResource.newResult(true).addPath(1, 10).build();
        String s = r.toString();
        assertTrue(s.contains("true"));
        assertTrue(s.contains("Path"));
    }

    // === Path class ===

    @Test
    @DisplayName("Path toString")
    void testPathToString() {
        TrafficResource.Path p = new TrafficResource.Path(42, 420);
        assertTrue(p.toString().contains("42"));
        assertTrue(p.toString().contains("420"));
    }
}
