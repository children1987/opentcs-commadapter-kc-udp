package com.kecong.opentcs.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KecongProtocolFrame — encode/decode cycle, edge cases.
 */
@DisplayName("KecongProtocolFrame")
class KecongProtocolFrameTest {

    private static final byte[] TEST_AUTH = "TESTAUTH12345678".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    @DisplayName("Encode and decode a simple request frame")
    void testEncodeDecodeRequest() {
        byte[] data = new byte[]{0x01, 0x02, 0x03};
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH)
                .asRequest()
                .sequenceNumber(42)
                .commandCode((byte) 0xAF)
                .data(data)
                .build();

        byte[] encoded = frame.encode();
        assertEquals(KecongProtocolFrame.HEADER_SIZE + 3 + 4, encoded.length);

        KecongProtocolFrame decoded = KecongProtocolFrame.decode(encoded);
        assertEquals(42, decoded.getSequenceNumber());
        assertEquals((byte) 0xAF, decoded.getCommandCode());
        assertTrue(decoded.isRequest());
        assertFalse(decoded.isResponse());
        assertEquals(3, decoded.getDataLength());
        assertArrayEquals(data, decoded.getData());
    }

    @Test
    @DisplayName("Encode and decode a response frame")
    void testEncodeDecodeResponse() {
        byte[] data = new byte[]{0x10, 0x20};
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH)
                .asResponse()
                .sequenceNumber(99)
                .commandCode((byte) 0xAF)
                .executionCode((byte) 0x00)
                .data(data)
                .build();

        byte[] encoded = frame.encode();
        KecongProtocolFrame decoded = KecongProtocolFrame.decode(encoded);
        assertTrue(decoded.isResponse());
        assertEquals(99, decoded.getSequenceNumber());
        assertEquals((byte) 0x00, decoded.getExecutionCode());
        assertEquals(2, decoded.getDataLength());
    }

    @Test
    @DisplayName("Frame with empty data")
    void testEmptyData() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH)
                .asRequest()
                .sequenceNumber(0)
                .commandCode((byte) 0xAF)
                .data(new byte[0])
                .build();

        byte[] encoded = frame.encode();
        assertEquals(KecongProtocolFrame.HEADER_SIZE + 4, encoded.length);

        KecongProtocolFrame decoded = KecongProtocolFrame.decode(encoded);
        assertEquals(0, decoded.getDataLength());
    }

    @Test
    @DisplayName("Frame with maximum data size (512 bytes)")
    void testMaxDataSize() {
        byte[] data = new byte[512];
        for (int i = 0; i < 512; i++) data[i] = (byte) (i & 0xFF);

        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH)
                .asRequest()
                .sequenceNumber(1)
                .commandCode((byte) 0xAE)
                .data(data)
                .build();

        byte[] encoded = frame.encode();
        assertEquals(KecongProtocolFrame.HEADER_SIZE + 512 + 4, encoded.length);

        KecongProtocolFrame decoded = KecongProtocolFrame.decode(encoded);
        assertEquals(512, decoded.getDataLength());
        assertArrayEquals(data, decoded.getData());
    }

    @Test
    @DisplayName("Decode fails with too-short frame")
    void testDecodeTooShort() {
        byte[] tooShort = new byte[10];
        assertThrows(IllegalArgumentException.class, () -> KecongProtocolFrame.decode(tooShort));
    }

    @Test
    @DisplayName("Decode fails with null frame")
    void testDecodeNull() {
        assertThrows(IllegalArgumentException.class, () -> KecongProtocolFrame.decode(null));
    }

    @Test
    @DisplayName("Encode fails with data exceeding max size")
    void testEncodeDataTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> {
            KecongProtocolFrame.builder()
                    .authCode(TEST_AUTH)
                    .data(new byte[513])
                    .build()
                    .encode();
        });
    }

    @Test
    @DisplayName("Sequence number wraps at 16 bits")
    void testSequenceNumberWrap() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH)
                .asRequest()
                .sequenceNumber(0xFFFF)
                .commandCode((byte) 0x01)
                .build();

        byte[] encoded = frame.encode();
        KecongProtocolFrame decoded = KecongProtocolFrame.decode(encoded);
        assertEquals(0xFFFF, decoded.getSequenceNumber());
    }

    @Test
    @DisplayName("Builder defaults create valid frame")
    void testBuilderDefaults() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH)
                .commandCode((byte) 0x01)
                .build();

        assertTrue(frame.isRequest());
        assertEquals(KecongProtocolFrame.PROTOCOL_VERSION, frame.getProtocolVersion());
        assertEquals(KecongProtocolFrame.SERVICE_CODE, frame.getServiceCode());
        assertEquals(0, frame.getSequenceNumber());
        assertEquals(0, frame.getDataLength());
    }

    @Test
    @DisplayName("Auth code string constructor pads to 16 bytes")
    void testAuthCodeStringPadding() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode("SHORT")
                .commandCode((byte) 0x01)
                .build();

        byte[] auth = frame.getAuthCode();
        assertEquals(16, auth.length);
        assertEquals('S', auth[0]);
        assertEquals('H', auth[1]);
        assertEquals('O', auth[2]);
        assertEquals('R', auth[3]);
        assertEquals('T', auth[4]);
        assertEquals(0, auth[5]);  // padded
    }

    @Test
    @DisplayName("toString includes key frame fields")
    void testToString() {
        KecongProtocolFrame frame = KecongProtocolFrame.builder()
                .authCode(TEST_AUTH)
                .asRequest()
                .sequenceNumber(7)
                .commandCode((byte) 0xAF)
                .data(new byte[]{1, 2})
                .build();

        String s = frame.toString();
        assertTrue(s.contains("REQ"));
        assertTrue(s.contains("7"));
        assertTrue(s.contains("AF"));
        assertTrue(s.contains("2"));
    }
}
