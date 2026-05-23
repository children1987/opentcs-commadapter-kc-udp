package com.kecong.opentcs.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for KecongProtocolFrame to push coverage to 100%.
 */
@DisplayName("KecongProtocolFrame Edge Cases")
class KecongProtocolFrameEdgeCaseTest {

    private static final byte[] TEST_AUTH = "TESTAUTH12345678".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    @DisplayName("toString for response frame includes RESP")
    void testToStringResponse() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH).asResponse().sequenceNumber(3)
                .commandCode((byte) 0xAF).executionCode((byte) 0x01)
                .data(new byte[]{0x10}).build();

        String s = frame.toString();
        assertTrue(s.contains("RESP"));
        assertTrue(s.contains("0x01"));
    }

    @Test
    @DisplayName("Decode frame with exact header + data length")
    void testDecodeExactLength() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH).asResponse().sequenceNumber(0)
                .commandCode((byte) 0x01).executionCode((byte) 0x00)
                .data(new byte[]{1, 2, 3, 4}).build();

        byte[] encoded = frame.encode();
        // Trim to exact HEADER_SIZE + dataLength
        int dataLen = 4;
        byte[] exact = new byte[KecongProtocolFrame.HEADER_SIZE + dataLen];
        System.arraycopy(encoded, 0, exact, 0, exact.length);

        KecongProtocolFrame decoded = KecongProtocolFrame.decode(exact);
        assertEquals(dataLen, decoded.getDataLength());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, decoded.getData());
    }

    @Test
    @DisplayName("Decode fails with truncated frame (header + data mismatch)")
    void testDecodeTruncated() {
        byte[] truncated = new byte[KecongProtocolFrame.HEADER_SIZE + 5];
        // The data length in header says 10, but only 5 bytes available
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(truncated)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[16]); // auth
        buf.put((byte) 1);     // version
        buf.put((byte) 0);     // request
        buf.putShort((short) 0); // seq
        buf.put((byte) 0x10);  // service
        buf.put((byte) 0);     // cmd
        buf.put((byte) 0);     // exec
        buf.put((byte) 0);     // reserved
        buf.putShort((short) 10); // data length = 10
        buf.putShort((short) 0);

        assertThrows(IllegalArgumentException.class, () ->
                KecongProtocolFrame.decode(truncated));
    }

    @Test
    @DisplayName("Auth code string longer than 16 chars gets truncated")
    void testAuthCodeLongString() {
        String longAuth = "THIS_IS_A_LONG_AUTH_CODE_STRING_OVER_16";
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(longAuth).commandCode((byte) 0x01).build();

        byte[] auth = frame.getAuthCode();
        assertEquals(16, auth.length);
        assertEquals('T', auth[0]);
        assertEquals('H', auth[1]);
        assertEquals('I', auth[2]);
        assertEquals('S', auth[3]);
        // Should be truncated at 16 bytes
    }

    @Test
    @DisplayName("Service code and protocol version are correct")
    void testConstants() {
        assertEquals((byte) 0x01, KecongProtocolFrame.PROTOCOL_VERSION);
        assertEquals((byte) 0x10, KecongProtocolFrame.SERVICE_CODE);
        assertEquals((byte) 0x00, KecongProtocolFrame.MSG_TYPE_REQUEST);
        assertEquals((byte) 0x01, KecongProtocolFrame.MSG_TYPE_RESPONSE);
        assertEquals(28, KecongProtocolFrame.HEADER_SIZE);
        assertEquals(512, KecongProtocolFrame.MAX_DATA_SIZE);
    }

    @Test
    @DisplayName("Builder asResponse sets execution code")
    void testBuilderAsResponse() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH).asResponse().sequenceNumber(5)
                .commandCode((byte) 0xAF).executionCode((byte) 0x02).build();

        assertTrue(frame.isResponse());
        assertEquals((byte) 0x02, frame.getExecutionCode());
    }
}
