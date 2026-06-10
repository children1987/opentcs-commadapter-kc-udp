package com.kecong.opentcs.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kecong.opentcs.protocol.model.VarReadRequest.VarMember;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage gap tests for VarReadRequest, VarReadResponse, VarWriteRequest, VarWriteMember, VarMember.
 *
 * <p>Fills remaining coverage gaps: equals/hashCode/toString for non-matching types,
 * boundary rejection paths, getByte/getUnsignedShort edge cases.</p>
 */
@DisplayName("VarRead/Write Model — Full Coverage")
class VarModelCoverageTest {

    // ── VarMember ──

    @Test
    @DisplayName("VarMember: equals with non-VarMember returns false")
    void testVarMemberEqualsWrongType() {
        VarMember m = new VarMember(0, 2);
        assertNotEquals("not a VarMember", m);
        assertNotEquals(null, m);
    }

    @Test
    @DisplayName("VarMember: equals same reference")
    void testVarMemberEqualsSameRef() {
        VarMember m = new VarMember(0, 2);
        assertEquals(m, m);
    }

    @Test
    @DisplayName("VarMember: equals different offset")
    void testVarMemberEqualsDiffOffset() {
        assertNotEquals(new VarMember(0, 2), new VarMember(2, 2));
    }

    @Test
    @DisplayName("VarMember: equals different length")
    void testVarMemberEqualsDiffLength() {
        assertNotEquals(new VarMember(0, 2), new VarMember(0, 4));
    }

    @Test
    @DisplayName("VarMember: toString format")
    void testVarMemberToString() {
        VarMember m = new VarMember(0x18, 4);
        String s = m.toString();
        assertTrue(s.contains("0x18"));
        assertTrue(s.contains("4"));
    }

    @Test
    @DisplayName("VarMember: hashCode consistent with equals")
    void testVarMemberHashCode() {
        VarMember m1 = new VarMember(0, 2);
        VarMember m2 = new VarMember(0, 2);
        VarMember m3 = new VarMember(2, 4);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertNotEquals(m1.hashCode(), m3.hashCode());
    }

    @Test
    @DisplayName("VarMember: valid boundary values (1 and 4)")
    void testVarMemberBoundaryLengths() {
        assertNotNull(new VarMember(0, 1));
        assertNotNull(new VarMember(0, 4));
    }

    @Test
    @DisplayName("VarMember: getOffset/getLength")
    void testVarMemberGetters() {
        VarMember m = new VarMember(10, 3);
        assertEquals(10, m.getOffset());
        assertEquals(3, m.getLength());
    }

    // ── VarReadRequest ──

    @Test
    @DisplayName("VarReadRequest: rejects empty members list")
    void testVarReadRequestRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () ->
                new VarReadRequest("AAA", Collections.emptyList()));
    }

    @Test
    @DisplayName("VarReadRequest: rejects null varName")
    void testVarReadRequestRejectsNullName() {
        assertThrows(NullPointerException.class, () ->
                new VarReadRequest(null, Arrays.asList(new VarMember(0, 2))));
    }

    @Test
    @DisplayName("VarReadRequest: equals with non-VarReadRequest")
    void testVarReadRequestEqualsWrongType() {
        VarReadRequest r = new VarReadRequest("AAA", 0, 2);
        assertNotEquals("not a VarReadRequest", r);
        assertNotEquals(null, r);
    }

    @Test
    @DisplayName("VarReadRequest: equals same reference")
    void testVarReadRequestEqualsSameRef() {
        VarReadRequest r = new VarReadRequest("AAA", 0, 2);
        assertEquals(r, r);
    }

    @Test
    @DisplayName("VarReadRequest: equals different name")
    void testVarReadRequestEqualsDiffName() {
        assertNotEquals(
                new VarReadRequest("AAA", 0, 2),
                new VarReadRequest("BBB", 0, 2));
    }

    @Test
    @DisplayName("VarReadRequest: equals different members")
    void testVarReadRequestEqualsDiffMembers() {
        assertNotEquals(
                new VarReadRequest("AAA", 0, 2),
                new VarReadRequest("AAA", 2, 2));
    }

    @Test
    @DisplayName("VarReadRequest: hashCode consistency")
    void testVarReadRequestHashCode() {
        VarReadRequest r1 = new VarReadRequest("AAA", 0, 2);
        VarReadRequest r2 = new VarReadRequest("AAA", 0, 2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    @DisplayName("VarReadRequest: toString")
    void testVarReadRequestToString() {
        VarReadRequest r = new VarReadRequest("B2GW", 0x18, 4);
        String s = r.toString();
        assertTrue(s.contains("B2GW"));
    }

    @Test
    @DisplayName("VarReadRequest: getStrValueSize with 2 members")
    void testVarReadRequestGetStrValueSize() {
        VarReadRequest r = new VarReadRequest("AAA",
                Arrays.asList(new VarMember(0, 2), new VarMember(2, 2)));
        // 16 (name) + 4 (count) + 2 * 4 (members) = 28
        assertEquals(28, r.getStrValueSize());
    }

    @Test
    @DisplayName("VarReadRequest: getVarName/getMembers")
    void testVarReadRequestGetters() {
        VarReadRequest r = new VarReadRequest("XYZ", 5, 1);
        assertEquals("XYZ", r.getVarName());
        assertEquals(1, r.getMembers().size());
        assertEquals(5, r.getMembers().get(0).getOffset());
        assertEquals(1, r.getMembers().get(0).getLength());
    }

    @Test
    @DisplayName("VarReadRequest: getMembers returns unmodifiable")
    void testVarReadRequestUnmodifiableMembers() {
        VarReadRequest r = new VarReadRequest("AAA", 0, 2);
        assertThrows(UnsupportedOperationException.class, () ->
                r.getMembers().add(new VarMember(0, 2)));
    }

    // ── VarReadResponse ──

    @Test
    @DisplayName("VarReadResponse: getByte")
    void testVarReadResponseGetByte() {
        byte[] raw = hexToBytes("01000000040000002A000000");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertNotNull(r);
        assertEquals(0x2A, r.getByte(0));
        assertEquals(0, r.getByte(1));
    }

    @Test
    @DisplayName("VarReadResponse: getByte with negative offset")
    void testVarReadResponseGetByteNegativeOffset() {
        byte[] raw = hexToBytes("01000000040000002A000000");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertThrows(IndexOutOfBoundsException.class, () -> r.getByte(-1));
    }

    @Test
    @DisplayName("VarReadResponse: getByte out of bounds")
    void testVarReadResponseGetByteOOB() {
        byte[] raw = hexToBytes("01000000040000002A000000");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertThrows(IndexOutOfBoundsException.class, () -> r.getByte(4));
    }

    @Test
    @DisplayName("VarReadResponse: getUnsignedShort with negative offset")
    void testVarReadResponseGetUnsignedShortNegative() {
        byte[] raw = hexToBytes("01000000100000004f01000050010000");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertThrows(IndexOutOfBoundsException.class, () -> r.getUnsignedShort(-1));
    }

    @Test
    @DisplayName("VarReadResponse: getUnsignedShort out of bounds")
    void testVarReadResponseGetUnsignedShortOOB() {
        byte[] raw = hexToBytes("01000000100000004f01000050010000");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertThrows(IndexOutOfBoundsException.class, () -> r.getUnsignedShort(7));
    }

    @Test
    @DisplayName("VarReadResponse: getInt with negative offset")
    void testVarReadResponseGetIntNegative() {
        byte[] raw = hexToBytes("010000000400000068F00100");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertThrows(IndexOutOfBoundsException.class, () -> r.getInt(-1));
    }

    @Test
    @DisplayName("VarReadResponse: toString with empty values")
    void testVarReadResponseToStringEmpty() {
        byte[] raw = hexToBytes("0100000000000000");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertNotNull(r);
        String s = r.toString();
        assertTrue(s.contains("valueId=1"));
        assertTrue(s.contains("dataLen=0"));
    }

    @Test
    @DisplayName("VarReadResponse: getValueId/getDataLength/getValues")
    void testVarReadResponseGetters() {
        byte[] raw = hexToBytes("2A0000000800000001000000");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertNotNull(r);
        assertEquals(42, r.getValueId());
        assertEquals(8, r.getDataLength());
        // dataLen=8, available=12-8=4, min=4
        assertEquals(4, r.getValues().length);
    }

    @Test
    @DisplayName("VarReadResponse: getValues returns defensive copy")
    void testVarReadResponseGetValuesDefensive() {
        byte[] raw = hexToBytes("010000000400000068F00100");
        VarReadResponse r = VarReadResponse.decode(raw);
        byte[] v1 = r.getValues();
        byte[] v2 = r.getValues();
        assertNotSame(v1, v2);
        v1[0] = (byte) 0xFF;
        assertNotEquals(v1[0], v2[0]);
    }

    @Test
    @DisplayName("VarReadResponse: dataLen larger than available truncated correctly")
    void testVarReadResponseDataLenTruncation() {
        // dataLen=100 but only 12 bytes total, 12-8=4 available
        byte[] raw = hexToBytes("010000006400000068F00100");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertNotNull(r);
        assertEquals(100, r.getDataLength()); // reported as-is
        assertEquals(4, r.getValues().length); // truncated to available (12-8=4)
    }

    @Test
    @DisplayName("VarReadResponse: getUnsignedShort with large value")
    void testVarReadResponseGetUnsignedShortMax() {
        byte[] raw = hexToBytes("0100000004000000FFFF0000");
        VarReadResponse r = VarReadResponse.decode(raw);
        assertNotNull(r);
        assertEquals(0xFFFF, r.getUnsignedShort(0));
    }

    // ── VarWriteMember ──

    @Test
    @DisplayName("VarWriteMember: equals with non-VarWriteMember")
    void testVarWriteMemberEqualsWrongType() {
        VarWriteMember m = new VarWriteMember(0, 2, 100);
        assertNotEquals("not a member", m);
        assertNotEquals(null, m);
    }

    @Test
    @DisplayName("VarWriteMember: equals same ref")
    void testVarWriteMemberEqualsSameRef() {
        VarWriteMember m = new VarWriteMember(0, 2, 100);
        assertEquals(m, m);
    }

    @Test
    @DisplayName("VarWriteMember: equals different values")
    void testVarWriteMemberEqualsDiff() {
        assertNotEquals(
                new VarWriteMember(0, 2, 100),
                new VarWriteMember(0, 2, 200));
        assertNotEquals(
                new VarWriteMember(0, 2, 100),
                new VarWriteMember(2, 2, 100));
        assertNotEquals(
                new VarWriteMember(0, 2, 100),
                new VarWriteMember(0, 4, 100));
    }

    @Test
    @DisplayName("VarWriteMember: hashCode consistency")
    void testVarWriteMemberHashCode() {
        VarWriteMember m1 = new VarWriteMember(0, 2, 333);
        VarWriteMember m2 = new VarWriteMember(0, 2, 333);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    @DisplayName("VarWriteMember: toString")
    void testVarWriteMemberToString() {
        VarWriteMember m = new VarWriteMember(0, 2, 333);
        String s = m.toString();
        assertTrue(s.contains("333"));
        assertTrue(s.contains("0x00"));
    }

    @Test
    @DisplayName("VarWriteMember: getters")
    void testVarWriteMemberGetters() {
        VarWriteMember m = new VarWriteMember(5, 3, 999);
        assertEquals(5, m.getOffset());
        assertEquals(3, m.getLength());
        assertEquals(999, m.getValue());
    }

    @Test
    @DisplayName("VarWriteMember: valid boundary length 1 and 4")
    void testVarWriteMemberBoundary() {
        assertNotNull(new VarWriteMember(0, 1, 255));
        assertNotNull(new VarWriteMember(0, 4, Integer.MAX_VALUE));
    }

    // ── VarWriteRequest ──

    @Test
    @DisplayName("VarWriteRequest: rejects empty members list")
    void testVarWriteRequestRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () ->
                new VarWriteRequest("AAA", Collections.emptyList()));
    }

    @Test
    @DisplayName("VarWriteRequest: rejects null varName")
    void testVarWriteRequestRejectsNullName() {
        assertThrows(NullPointerException.class, () ->
                new VarWriteRequest(null, Arrays.asList(new VarWriteMember(0, 2, 1))));
    }

    @Test
    @DisplayName("VarWriteRequest: equals with non-VarWriteRequest")
    void testVarWriteRequestEqualsWrongType() {
        VarWriteRequest r = new VarWriteRequest("AAA", 0, 2, 333);
        assertNotEquals("not a VarWriteRequest", r);
        assertNotEquals(null, r);
    }

    @Test
    @DisplayName("VarWriteRequest: equals same ref")
    void testVarWriteRequestEqualsSameRef() {
        VarWriteRequest r = new VarWriteRequest("AAA", 0, 2, 333);
        assertEquals(r, r);
    }

    @Test
    @DisplayName("VarWriteRequest: equals different")
    void testVarWriteRequestEqualsDiff() {
        assertNotEquals(
                new VarWriteRequest("AAA", 0, 2, 333),
                new VarWriteRequest("BBB", 0, 2, 333));
    }

    @Test
    @DisplayName("VarWriteRequest: hashCode consistency")
    void testVarWriteRequestHashCode() {
        VarWriteRequest r1 = new VarWriteRequest("AAA",
                Arrays.asList(new VarWriteMember(0, 2, 333), new VarWriteMember(2, 2, 334)));
        VarWriteRequest r2 = new VarWriteRequest("AAA",
                Arrays.asList(new VarWriteMember(0, 2, 333), new VarWriteMember(2, 2, 334)));
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    @DisplayName("VarWriteRequest: toString")
    void testVarWriteRequestToString() {
        VarWriteRequest r = new VarWriteRequest("AAA", 0, 2, 333);
        String s = r.toString();
        assertTrue(s.contains("AAA"));
    }

    @Test
    @DisplayName("VarWriteRequest: getStrValueSize with multiple members")
    void testVarWriteRequestGetStrValueSize() {
        VarWriteRequest r = new VarWriteRequest("AAA",
                Arrays.asList(new VarWriteMember(0, 2, 333), new VarWriteMember(2, 2, 334)));
        // 16 (name) + 4 (count) + 2 * 8 (members) = 36
        assertEquals(36, r.getStrValueSize());
    }

    @Test
    @DisplayName("VarWriteRequest: getVarName/getMembers")
    void testVarWriteRequestGetters() {
        VarWriteRequest r = new VarWriteRequest("XYZ", 0, 4, 99999);
        assertEquals("XYZ", r.getVarName());
        assertEquals(1, r.getMembers().size());
        assertEquals(99999, r.getMembers().get(0).getValue());
    }

    @Test
    @DisplayName("VarWriteRequest: getMembers returns unmodifiable")
    void testVarWriteRequestUnmodifiableMembers() {
        VarWriteRequest r = new VarWriteRequest("AAA", 0, 2, 333);
        assertThrows(UnsupportedOperationException.class, () ->
                r.getMembers().add(new VarWriteMember(0, 2, 1)));
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
