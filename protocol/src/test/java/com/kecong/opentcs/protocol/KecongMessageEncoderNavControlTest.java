package com.kecong.opentcs.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for encodeNavControl (0x16 NAV_CONTROL request).
 */
@DisplayName("encodeNavControl")
class KecongMessageEncoderNavControlTest {

    @Test
    @DisplayName("Encodes start operation to point '2' (432 bytes)")
    void testEncodeStartToPoint2() {
        byte[] data = KecongMessageEncoder.encodeNavControl("2", 0);
        assertEquals(432, data.length);
        assertEquals(0, data[0]);  // operation = start
        assertEquals(0, data[1]);  // nav_mode = to path point
        assertEquals(0, data[2]);  // specify_path = no
        assertEquals(0, data[3]);  // traffic = off
        // Point ID "2" at bytes 4-11, ASCII
        byte[] ptBytes = new byte[8];
        System.arraycopy(data, 4, ptBytes, 0, 8);
        assertEquals("2", new String(ptBytes, StandardCharsets.US_ASCII).trim());
        // Bytes 12-431 should be all zeros
        for (int i = 12; i < 432; i++) {
            assertEquals(0, data[i], "byte " + i + " should be 0");
        }
    }

    @Test
    @DisplayName("Encodes cancel operation")
    void testEncodeCancel() {
        byte[] data = KecongMessageEncoder.encodeNavControl("1", 1);
        assertEquals(432, data.length);
        assertEquals(1, data[0]);  // operation = cancel
    }

    @Test
    @DisplayName("Encodes pause operation")
    void testEncodePause() {
        byte[] data = KecongMessageEncoder.encodeNavControl("3", 2);
        assertEquals(432, data.length);
        assertEquals(2, data[0]);  // operation = pause
    }

    @Test
    @DisplayName("Encodes resume operation")
    void testEncodeResume() {
        byte[] data = KecongMessageEncoder.encodeNavControl("5", 3);
        assertEquals(432, data.length);
        assertEquals(3, data[0]);  // operation = resume
    }

    @Test
    @DisplayName("Encodes create+pause operation")
    void testEncodeCreatePause() {
        byte[] data = KecongMessageEncoder.encodeNavControl("7", 4);
        assertEquals(432, data.length);
        assertEquals(4, data[0]);  // operation = create+pause
    }

    @Test
    @DisplayName("Point ID with multiple digits")
    void testEncodeMultiDigitPoint() {
        byte[] data = KecongMessageEncoder.encodeNavControl("123", 0);
        assertEquals(432, data.length);
        byte[] ptBytes = new byte[8];
        System.arraycopy(data, 4, ptBytes, 0, 8);
        assertEquals("123", new String(ptBytes, StandardCharsets.US_ASCII).trim());
    }

    @Test
    @DisplayName("Point ID length clamped to 8 bytes")
    void testEncodeLongPointId() {
        byte[] data = KecongMessageEncoder.encodeNavControl("1234567890", 0);
        assertEquals(432, data.length);
        byte[] ptBytes = new byte[8];
        System.arraycopy(data, 4, ptBytes, 0, 8);
        assertEquals("12345678", new String(ptBytes, StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("All 432 bytes initialized to zero except header+point")
    void testAllZeros() {
        byte[] data = KecongMessageEncoder.encodeNavControl("1", 0);
        int nonZero = 0;
        for (int i = 0; i < 432; i++) {
            if (data[i] != 0) nonZero++;
        }
        // byte[0]=0(op=start), byte[4]='1'(0x31) → 1 non-zero byte
        // byte[1..3] are zeros (nav_mode=0, specify=0, traffic=0)
        assertEquals(1, nonZero, "only point ID '1' (0x31) at byte[4] should be non-zero");
    }
}
