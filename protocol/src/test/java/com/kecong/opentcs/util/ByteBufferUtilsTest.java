package com.kecong.opentcs.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ByteBufferUtils.
 */
@DisplayName("ByteBufferUtils")
class ByteBufferUtilsTest {

    @Test
    @DisplayName("allocate creates little-endian buffer")
    void testAllocateLittleEndian() {
        ByteBuffer buf = ByteBufferUtils.allocate(16);
        assertEquals(ByteOrder.LITTLE_ENDIAN, buf.order());
        assertEquals(16, buf.capacity());
    }

    @Test
    @DisplayName("wrap creates little-endian buffer from array")
    void testWrapLittleEndian() {
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};
        ByteBuffer buf = ByteBufferUtils.wrap(data);
        assertEquals(ByteOrder.LITTLE_ENDIAN, buf.order());
        assertEquals(4, buf.remaining());
        assertEquals(0x01, buf.get());
    }

    @Test
    @DisplayName("putFixedString writes exact length")
    void testPutFixedStringExact() {
        ByteBuffer buf = ByteBufferUtils.allocate(10);
        ByteBufferUtils.putFixedString(buf, "HELLO", 5);
        buf.flip();
        byte[] result = new byte[5];
        buf.get(result);
        assertArrayEquals("HELLO".getBytes(java.nio.charset.StandardCharsets.US_ASCII), result);
    }

    @Test
    @DisplayName("putFixedString pads short strings with zeros")
    void testPutFixedStringPadding() {
        ByteBuffer buf = ByteBufferUtils.allocate(10);
        ByteBufferUtils.putFixedString(buf, "AB", 5);
        buf.flip();
        assertEquals('A', buf.get());
        assertEquals('B', buf.get());
        assertEquals(0, buf.get());
        assertEquals(0, buf.get());
        assertEquals(0, buf.get());
    }

    @Test
    @DisplayName("putFixedString truncates long strings")
    void testPutFixedStringTruncate() {
        ByteBuffer buf = ByteBufferUtils.allocate(5);
        ByteBufferUtils.putFixedString(buf, "HELLOWORLD", 5);
        buf.flip();
        byte[] result = new byte[5];
        buf.get(result);
        assertArrayEquals("HELLO".getBytes(java.nio.charset.StandardCharsets.US_ASCII), result);
    }

    @Test
    @DisplayName("getFixedString reads ASCII string")
    void testGetFixedStringNormal() {
        ByteBuffer buf = ByteBufferUtils.allocate(10);
        buf.put("TEST".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buf.put(new byte[6]); // padding
        buf.flip();
        assertEquals("TEST", ByteBufferUtils.getFixedString(buf, 10));
    }

    @Test
    @DisplayName("getFixedString stops at null terminator")
    void testGetFixedStringNullTerminated() {
        ByteBuffer buf = ByteBufferUtils.allocate(16);
        buf.put("OK".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buf.put((byte) 0);
        buf.put(new byte[13]); // more padding
        buf.flip();
        assertEquals("OK", ByteBufferUtils.getFixedString(buf, 16));
    }

    @Test
    @DisplayName("alignTo4 adds padding when misaligned")
    void testAlignTo4Misaligned() {
        ByteBuffer buf = ByteBufferUtils.allocate(10);
        buf.put((byte) 1);
        buf.put((byte) 2);
        buf.put((byte) 3);
        // position is 3, not aligned
        ByteBufferUtils.alignTo4(buf);
        assertEquals(4, buf.position());  // padded to 4
    }

    @Test
    @DisplayName("alignTo4 no-op when already aligned")
    void testAlignTo4Aligned() {
        ByteBuffer buf = ByteBufferUtils.allocate(10);
        buf.putInt(42);
        // position is 4, already aligned
        ByteBufferUtils.alignTo4(buf);
        assertEquals(4, buf.position());
    }

    @Test
    @DisplayName("alignTo4 at position 0")
    void testAlignTo4Zero() {
        ByteBuffer buf = ByteBufferUtils.allocate(10);
        ByteBufferUtils.alignTo4(buf);
        assertEquals(0, buf.position());
    }
}
