package com.kecong.opentcs.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for 0x01 READ_VAR and 0x00 WRITE_VAR encoding/decoding.
 *
 * <p>Verified against real KeCong MRC controller (2026-06-10).</p>
 */
@DisplayName("0x00/0x01 Single Variable Read/Write")
class KecongReadWriteVarTest {

    // ── 0x01 READ_VAR encoding ──

    @Test
    @DisplayName("encodeReadVar: simple name")
    void testEncodeReadVarSimple() {
        byte[] data = KecongMessageEncoder.encodeReadVar("AAA");

        assertNotNull(data);
        assertEquals(16, data.length);

        // First 3 bytes = "AAA", rest zeros
        byte[] expected = "AAA".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], data[i], "byte " + i);
        }
        for (int i = expected.length; i < 16; i++) {
            assertEquals(0, data[i], "padding byte " + i);
        }
    }

    @Test
    @DisplayName("encodeReadVar: 16-char name (exact fit)")
    void testEncodeReadVarExact16() {
        String name = "ABCDEFGHIJKLMNOP"; // exactly 16 chars
        byte[] data = KecongMessageEncoder.encodeReadVar(name);

        assertEquals(16, data.length);
        assertArrayEquals(name.getBytes(StandardCharsets.US_ASCII), data);
    }

    @Test
    @DisplayName("encodeReadVar: B2GW (area variable)")
    void testEncodeReadVarB2GW() {
        byte[] data = KecongMessageEncoder.encodeReadVar("B2GW");

        assertEquals(16, data.length);
        // Matches Python test tool output
        byte[] expected = "B2GW".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], data[i]);
        }
        for (int i = 4; i < 16; i++) {
            assertEquals(0, data[i]);
        }
    }

    @Test
    @DisplayName("encodeReadVar: null/empty rejected")
    void testEncodeReadVarRejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeReadVar(null));
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeReadVar(""));
    }

    @Test
    @DisplayName("encodeReadVar: too long rejected")
    void testEncodeReadVarRejectsTooLong() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeReadVar("12345678901234567")); // 17 chars
    }

    // ── 0x00 WRITE_VAR encoding ──

    @Test
    @DisplayName("encodeWriteVar: 2-byte value (U16)")
    void testEncodeWriteVarU16() {
        byte[] value = new byte[]{(byte) 0x4D, 0x01}; // 333 in LE
        byte[] data = KecongMessageEncoder.encodeWriteVar("AAA", value);

        assertEquals(18, data.length); // 16 name + 2 value
        assertEquals('A', data[0]);
        assertEquals('A', data[1]);
        assertEquals('A', data[2]);
        assertEquals(0, data[3]); // padding
        assertEquals((byte) 0x4D, data[16]);
        assertEquals((byte) 0x01, data[17]);
    }

    @Test
    @DisplayName("encodeWriteVar: 4-byte value (DINT)")
    void testEncodeWriteVarDInt() {
        // DINT 127080 = 0x0001F068 → LE: 68 F0 01 00
        byte[] value = new byte[]{(byte) 0x68, (byte) 0xF0, 0x01, 0x00};
        byte[] data = KecongMessageEncoder.encodeWriteVar("B2GW", value);

        assertEquals(20, data.length); // 16 name + 4 value
        // Name
        assertEquals('B', data[0]);
        assertEquals('2', data[1]);
        assertEquals('G', data[2]);
        assertEquals('W', data[3]);
        // Value
        assertEquals((byte) 0x68, data[16]);
        assertEquals((byte) 0xF0, data[17]);
        assertEquals(0x01, data[18]);
        assertEquals(0x00, data[19]);
    }

    @Test
    @DisplayName("encodeWriteVar: 1-byte value (BOOL/BYTE)")
    void testEncodeWriteVarByte() {
        byte[] value = new byte[]{0x01};
        byte[] data = KecongMessageEncoder.encodeWriteVar("Screen.ForkUp", value);

        assertEquals(17, data.length);
        assertEquals('S', data[0]);
        assertEquals(0x01, data[16]);
    }

    @Test
    @DisplayName("encodeWriteVar: null/empty varName rejected")
    void testEncodeWriteVarRejectsInvalidName() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeWriteVar(null, new byte[]{0x01}));
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeWriteVar("", new byte[]{0x01}));
    }

    @Test
    @DisplayName("encodeWriteVar: null value rejected")
    void testEncodeWriteVarRejectsNullValue() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeWriteVar("AAA", null));
    }

    // ── 0x01 READ_VAR response decoding ──

    @Test
    @DisplayName("decodeReadVarResponse: AAA with 4-byte value")
    void testDecodeReadVarAAA() {
        // Real response: name "AAA" + value [4D 01 4E 01] (INT16=333, UINT16=334)
        byte[] response = new byte[20];
        System.arraycopy("AAA".getBytes(StandardCharsets.US_ASCII), 0, response, 0, 3);
        response[16] = (byte) 0x4D;
        response[17] = 0x01;
        response[18] = (byte) 0x4E;
        response[19] = 0x01;

        assertEquals("AAA", KecongMessageDecoder.decodeReadVarResponseName(response));

        byte[] value = KecongMessageDecoder.decodeReadVarResponse(response);
        assertNotNull(value);
        assertEquals(4, value.length);
        assertEquals((byte) 0x4D, value[0]);
        assertEquals(0x01, value[1]);
        assertEquals((byte) 0x4E, value[2]);
        assertEquals(0x01, value[3]);
    }

    @Test
    @DisplayName("decodeReadVarResponse: 2-byte BOOL variable")
    void testDecodeReadVar2Byte() {
        // For BOOL-type variables, response is name(16) + 2 bytes
        byte[] response = new byte[18];
        System.arraycopy("Screen.ForkUp".getBytes(StandardCharsets.US_ASCII), 0, response, 0, 13);
        response[16] = 0x01;
        response[17] = 0x00;

        assertEquals("Screen.ForkUp", KecongMessageDecoder.decodeReadVarResponseName(response));

        byte[] value = KecongMessageDecoder.decodeReadVarResponse(response);
        assertNotNull(value);
        assertEquals(2, value.length);
        assertEquals(0x01, value[0]);
    }

    @Test
    @DisplayName("decodeReadVarResponse: minimum valid (17 bytes)")
    void testDecodeReadVarMinimal() {
        byte[] response = new byte[17];
        response[0] = 'X';
        response[16] = 0x01; // 1-byte value

        byte[] value = KecongMessageDecoder.decodeReadVarResponse(response);
        assertNotNull(value);
        assertEquals(1, value.length);
        assertEquals(0x01, value[0]);
    }

    @Test
    @DisplayName("decodeReadVarResponse: null/too-short returns null")
    void testDecodeReadVarInvalid() {
        assertNull(KecongMessageDecoder.decodeReadVarResponse(null));
        assertNull(KecongMessageDecoder.decodeReadVarResponse(new byte[0]));
        assertNull(KecongMessageDecoder.decodeReadVarResponse(new byte[15]));
        assertNull(KecongMessageDecoder.decodeReadVarResponse(new byte[16])); // no value
    }

    @Test
    @DisplayName("decodeReadVarResponseName: extracts and trims name")
    void testDecodeReadVarResponseName() {
        byte[] data = new byte[20];
        System.arraycopy("B2GW".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);

        assertEquals("B2GW", KecongMessageDecoder.decodeReadVarResponseName(data));

        // Half-padded name
        byte[] data2 = new byte[20];
        data2[0] = 'X';
        assertEquals("X", KecongMessageDecoder.decodeReadVarResponseName(data2));
    }

    @Test
    @DisplayName("encodeWriteVar matches Python tool output for AAA")
    void testEncodeWriteVarMatchesPython() {
        // Python: struct.pack('<hH', 333, 334) = 4D 01 4E 01
        byte[] value = new byte[]{(byte) 0x4D, 0x01, (byte) 0x4E, 0x01};
        byte[] data = KecongMessageEncoder.encodeWriteVar("AAA", value);

        // Expected: "AAA" + 13 zeros + 4D 01 4E 01
        byte[] expected = new byte[20];
        System.arraycopy("AAA".getBytes(StandardCharsets.US_ASCII), 0, expected, 0, 3);
        System.arraycopy(value, 0, expected, 16, 4);

        assertArrayEquals(expected, data);
    }
}
