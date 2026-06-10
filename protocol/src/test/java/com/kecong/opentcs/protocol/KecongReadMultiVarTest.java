package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.VarReadRequest;
import com.kecong.opentcs.protocol.model.VarReadRequest.VarMember;
import com.kecong.opentcs.protocol.model.VarReadResponse;
import com.kecong.opentcs.protocol.model.VarWriteRequest;
import com.kecong.opentcs.protocol.model.VarWriteMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for 0x02 READ_MULTI_VAR and 0x03 WRITE_MULTI_VAR encoding/decoding.
 *
 * <p>
 * All formats verified against a real KeCong MRC controller on 2026-06-10.
 * Key findings:
 * <ul>
 *   <li>Count is U32 (4 bytes), not U8</li>
 *   <li>0x02 header: [U32 count][U32 ValueID]</li>
 *   <li>0x03 header: [U32 count] only — NO ValueID</li>
 *   <li>0x02 response DataLen is U32, not U16</li>
 * </ul>
 * </p>
 */
@DisplayName("0x02/0x03 Variable Operations")
class KecongReadMultiVarTest {

    // ── 0x02 Encoding tests (corrected format) ──

    @Test
    @DisplayName("Encode 0x02 single variable, single member — U32 count + U32 ValueID")
    void testEncodeSingleVarSingleMember() {
        VarReadRequest req = new VarReadRequest("AAA", 0, 2);

        byte[] data = KecongMessageEncoder.encodeReadMultiVar(
                Collections.singletonList(req), 0);

        assertNotNull(data);
        assertEquals(32, data.length); // count(4) + valueId(4) + name(16) + mcount(4) + member(4)

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // Header: U32 count + U32 ValueID
        assertEquals(1, buf.getInt(), "var count (U32)");
        assertEquals(0, buf.getInt(), "valueId (U32)");

        // StrValue
        byte[] nameBytes = new byte[16];
        buf.get(nameBytes);
        assertEquals("AAA", new String(nameBytes).trim(), "var name");

        assertEquals(1, buf.getInt(), "member count");

        // ValueMember
        assertEquals(0, buf.getShort() & 0xFFFF, "member offset");
        assertEquals(2, buf.getShort() & 0xFFFF, "member length");
    }

    @Test
    @DisplayName("Encode 0x02 multiple members — matches tool output bytes")
    void testEncodeMultiMembers() {
        VarReadRequest req = new VarReadRequest("AAA", Arrays.asList(
                new VarMember(0, 2),
                new VarMember(2, 2)
        ));

        byte[] data = KecongMessageEncoder.encodeReadMultiVar(
                Collections.singletonList(req), 0);

        // This should match the tool's output:
        // 01000000 00000000 41414100... 02000000 00000200 02000200
        byte[] expected = hexToBytes(
                "01000000" +                         // count=1 (U32)
                "00000000" +                         // valueId=0 (U32)
                "41414100" + "000000000000000000000000" + // "AAA" padded 16B
                "02000000" +                         // member count=2
                "0000" + "0200" +                    // member1: off=0 len=2
                "0200" + "0200"                      // member2: off=2 len=2
        );

        assertArrayEquals(expected, data, "0x02 request must match KeCong tool output");
    }

    // ── 0x03 Encoding tests ──

    @Test
    @DisplayName("Encode 0x03 single variable, two members — NO ValueID")
    void testEncodeWriteMultiVar() {
        VarWriteRequest req = new VarWriteRequest("AAA", Arrays.asList(
                new VarWriteMember(0, 2, 333),
                new VarWriteMember(2, 2, 334)
        ));

        byte[] data = KecongMessageEncoder.encodeWriteMultiVar(
                Collections.singletonList(req));

        assertNotNull(data);
        assertEquals(40, data.length); // count(4) + name(16) + mcount(4) + 2×member(8)

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(1, buf.getInt(), "var count (U32)");
        // NO ValueID for 0x03

        byte[] nameBytes = new byte[16];
        buf.get(nameBytes);
        assertEquals("AAA", new String(nameBytes).trim());

        assertEquals(2, buf.getInt(), "member count");

        // Member 1
        assertEquals(0, buf.getShort() & 0xFFFF, "m1 offset");
        assertEquals(2, buf.getShort() & 0xFFFF, "m1 length");
        assertEquals(333, buf.getInt(), "m1 value");

        // Member 2
        assertEquals(2, buf.getShort() & 0xFFFF, "m2 offset");
        assertEquals(2, buf.getShort() & 0xFFFF, "m2 length");
        assertEquals(334, buf.getInt(), "m2 value");
    }

    @Test
    @DisplayName("Encode 0x03 matches KeCong tool output")
    void testEncodeWriteMatchesTool() {
        VarWriteRequest req = new VarWriteRequest("AAA", Arrays.asList(
                new VarWriteMember(0, 2, 335),
                new VarWriteMember(2, 2, 336)
        ));

        byte[] data = KecongMessageEncoder.encodeWriteMultiVar(
                Collections.singletonList(req));

        // Verified against KeCong UDP debug tool output (2026-06-10):
        // 01000000 41414100... 02000000 000002004f010000 0200020050010000
        byte[] expected = hexToBytes(
                "01000000" +                         // count=1 (U32, NO ValueID)
                "41414100" + "000000000000000000000000" + // "AAA"
                "02000000" +                         // member count=2
                "0000" + "0200" + "4f010000" +       // off=0 len=2 val=335
                "0200" + "0200" + "50010000"         // off=2 len=2 val=336
        );

        assertArrayEquals(expected, data, "0x03 request must match KeCong tool output");
    }

    // ── 0x02 Response decoding tests (corrected U32 DataLen) ──

    @Test
    @DisplayName("Decode 0x02 response — U32 DataLen (not U16)")
    void testDecodeResponseU32DataLen() {
        // Real response from KeCong tool: ValueID=0, DataLen=16 (U32!), values
        byte[] raw = hexToBytes("00000000" + "10000000" + "4f010000" + "50010000");

        VarReadResponse resp = VarReadResponse.decode(raw);
        assertNotNull(resp);
        assertEquals(0, resp.getValueId());
        assertEquals(16, resp.getDataLength());  // U32
        assertEquals(335, resp.getInt(0), "AAA[0] INT16");
        assertEquals(336, resp.getUnsignedShort(4), "AAA[2] UINT16 (4B aligned)");
    }

    // ── Other tests (unchanged) ──

    @Test
    @DisplayName("Encode multiple variables")
    void testEncodeMultiVar() {
        VarReadRequest req1 = new VarReadRequest("AAA", 0, 2);
        VarReadRequest req2 = new VarReadRequest("BBB", 0, 4);

        byte[] data = KecongMessageEncoder.encodeReadMultiVar(
                Arrays.asList(req1, req2), 1);

        assertTrue(data.length > 32);

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(2, buf.getInt(), "var count");
        assertEquals(1, buf.getInt(), "valueId");
    }

    @Test
    @DisplayName("Encode rejects empty request list")
    void testEncodeRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeReadMultiVar(Collections.emptyList(), 1));
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeWriteMultiVar(Collections.emptyList()));
    }

    @Test
    @DisplayName("Encode rejects null request")
    void testEncodeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeReadMultiVar(null, 1));
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeWriteMultiVar(null));
    }

    @Test
    @DisplayName("VarMember rejects invalid length")
    void testVarMemberRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> new VarMember(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new VarMember(0, 5));
    }

    @Test
    @DisplayName("VarWriteMember rejects invalid length")
    void testVarWriteMemberRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> new VarWriteMember(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new VarWriteMember(0, 5, 1));
    }

    @Test
    @DisplayName("Decode 0x02 error response (dataLen=0)")
    void testDecodeErrorResponse() {
        byte[] raw = hexToBytes("01000000" + "00000000");
        VarReadResponse resp = VarReadResponse.decode(raw);
        assertNotNull(resp);
        assertEquals(0, resp.getDataLength(), "dataLen=0 indicates error");
        assertEquals(0, resp.getValues().length);
    }

    @Test
    @DisplayName("Decode returns null for invalid data")
    void testDecodeNull() {
        assertNull(VarReadResponse.decode(null));
        assertNull(VarReadResponse.decode(new byte[0]));
        assertNull(VarReadResponse.decode(new byte[4]));
    }

    @Test
    @DisplayName("Response getInt throws on out-of-bounds")
    void testResponseBoundsCheck() {
        byte[] raw = hexToBytes("00000000" + "04000000" + "68F00100");
        VarReadResponse resp = VarReadResponse.decode(raw);
        assertNotNull(resp);
        assertEquals(127080, resp.getInt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> resp.getInt(4));
    }

    // ── Utility ──

    private static byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
