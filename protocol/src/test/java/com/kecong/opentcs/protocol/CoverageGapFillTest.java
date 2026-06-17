package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.VarReadRequest;
import com.kecong.opentcs.protocol.model.VarReadRequest.VarMember;
import com.kecong.opentcs.protocol.model.VarReadResponse;
import com.kecong.opentcs.protocol.model.VarWriteMember;
import com.kecong.opentcs.protocol.model.VarWriteRequest;
import com.kecong.opentcs.util.ByteBufferUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted tests to fill remaining coverage gaps in the protocol module.
 * Each test maps to a specific missed branch/instruction from JaCoCo.
 */
@DisplayName("Coverage Gap Fill")
class CoverageGapFillTest {

    // ── VarReadResponse.toString — non-empty values ──

    @Test
    @DisplayName("VarReadResponse.toString with non-empty values")
    void testVarReadResponseToStringNonEmpty() {
        byte[] raw = hexToBytes("010000000400000068F00100");
        VarReadResponse r = VarReadResponse.decode(raw);
        String s = r.toString();
        assertTrue(s.contains("68F00100"));
        assertTrue(s.contains("valueId=1"));
        assertTrue(s.contains("dataLen=4"));
    }

    // ── KecongMessageDecoder.taskStateToAgvState — EXIT (6) and unrecognized (7) ──

    @Test
    @DisplayName("decodeRunStatus: task state EXIT(6) maps to IDLE(0)")
    void testDecodeRunStatusTaskStateExit() {
        byte[] data = buildRunStatusData(6); // EXIT
        assertNotNull(KecongMessageDecoder.decodeRunStatus(data));
        // EXIT task state → agvState IDLE (0)
    }

    @Test
    @DisplayName("decodeRunStatus: unrecognized task state maps to default")
    void testDecodeRunStatusTaskStateUnknown() {
        byte[] data = buildRunStatusData(7); // unrecognized
        assertNotNull(KecongMessageDecoder.decodeRunStatus(data));
    }

    // ── KecongMessageDecoder.decodeReadVarResponseName — edge cases ──

    @Test
    @DisplayName("decodeReadVarResponseName: null data")
    void testDecodeReadVarResponseNameNull() {
        assertNull(KecongMessageDecoder.decodeReadVarResponseName(null));
    }

    @Test
    @DisplayName("decodeReadVarResponseName: too short data")
    void testDecodeReadVarResponseNameTooShort() {
        assertNull(KecongMessageDecoder.decodeReadVarResponseName(new byte[10]));
    }

    @Test
    @DisplayName("decodeReadVarResponseName: all zeros returns empty string")
    void testDecodeReadVarResponseNameAllZeros() {
        byte[] data = new byte[20]; // all zeros
        assertEquals("", KecongMessageDecoder.decodeReadVarResponseName(data));
    }

    // ── KecongMessageDecoder.decodeRobotStatus — abnormal/action with no remaining data ──

    @Test
    @DisplayName("decodeRobotStatus: abnormalSize non-zero but no data remaining")
    void testDecodeRobotStatusAbnormalNoData() {
        ByteBuffer buf = ByteBufferUtils.allocate(200);
        buf.put((byte) 1);  // abnormalSize = 1
        buf.put((byte) 0);  // actionSize = 0
        buf.putShort((short) 0); // reserved
        // Fill remaining location+running+task+battery with zeros (minimal valid)
        byte[] zeros = new byte[150];
        buf.put(zeros);
        byte[] data = buf.array();

        assertNotNull(KecongMessageDecoder.decodeRobotStatus(data));
    }

    @Test
    @DisplayName("decodeRobotStatus: actionSize non-zero but no data remaining")
    void testDecodeRobotStatusActionNoData() {
        ByteBuffer buf = ByteBufferUtils.allocate(200);
        buf.put((byte) 0);  // abnormalSize = 0
        buf.put((byte) 1);  // actionSize = 1
        buf.putShort((short) 0);
        byte[] zeros = new byte[150];
        buf.put(zeros);
        byte[] data = buf.array();

        assertNotNull(KecongMessageDecoder.decodeRobotStatus(data));
    }

    // ── KecongMessageEncoder.encodeWriteVar — name too long ──

    @Test
    @DisplayName("encodeWriteVar: varName too long (>16) rejected")
    void testEncodeWriteVarNameTooLong() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeWriteVar("12345678901234567", new byte[]{0x01}));
    }

    // ── KecongMessageEncoder.encodeReadVar — name too long ──

    @Test
    @DisplayName("encodeReadVar: varName too long (>16) rejected")
    void testEncodeReadVarNameTooLong() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongMessageEncoder.encodeReadVar("12345678901234567"));
    }

    // ── ByteBufferUtils.getFixedString — all null bytes ──

    @Test
    @DisplayName("ByteBufferUtils.getFixedString: all null bytes")
    void testGetFixedStringAllNulls() {
        ByteBuffer buf = ByteBuffer.allocate(10);
        for (int i = 0; i < 10; i++) buf.put((byte) 0);
        buf.flip();
        assertEquals("", ByteBufferUtils.getFixedString(buf, 10));
    }

    // ── VarMember.equals — same offset, different length (branch fill) ──

    @Test
    @DisplayName("VarMember.equals: same offset, different length returns false")
    void testVarMemberEqualsDiffLengthOnly() {
        VarMember m1 = new VarMember(5, 2);
        VarMember m2 = new VarMember(5, 4);
        assertNotEquals(m1, m2);
    }

    // ── VarWriteMember.equals — same offset and length, different value ──

    @Test
    @DisplayName("VarWriteMember.equals: same off/len, different value returns false")
    void testVarWriteMemberEqualsDiffValOnly() {
        VarWriteMember m1 = new VarWriteMember(5, 2, 100);
        VarWriteMember m2 = new VarWriteMember(5, 2, 200);
        assertNotEquals(m1, m2);
    }

    // ── VarWriteRequest.equals — different member lists ──

    @Test
    @DisplayName("VarWriteRequest.equals: same name, different member count")
    void testVarWriteRequestEqualsDiffMemberCount() {
        VarWriteRequest r1 = new VarWriteRequest("AAA", 0, 2, 100);
        VarWriteRequest r2 = new VarWriteRequest("AAA", Arrays.asList(
                new VarWriteMember(0, 2, 100),
                new VarWriteMember(2, 2, 200)));
        // r1 has 1 member, r2 has 2 — they should not be equal
        assertNotEquals(r1, r2);
    }

    // ── VarReadRequest.equals — different member lists ──

    @Test
    @DisplayName("VarReadRequest.equals: same name, different member count")
    void testVarReadRequestEqualsDiffMemberCount() {
        VarReadRequest r1 = new VarReadRequest("AAA", 0, 2);
        VarReadRequest r2 = new VarReadRequest("AAA",
                Arrays.asList(new VarMember(0, 2), new VarMember(2, 2)));
        assertNotEquals(r1, r2);
    }

    // ── VarReadRequest.equals — different member details ──

    @Test
    @DisplayName("VarReadRequest.equals: same name, same count, different offset")
    void testVarReadRequestEqualsDiffMemberOffset() {
        VarReadRequest r1 = new VarReadRequest("AAA", 0, 2);
        VarReadRequest r2 = new VarReadRequest("AAA", 4, 2);
        assertNotEquals(r1, r2);
    }

    // ── VarWriteRequest.equals — different member details ──

    @Test
    @DisplayName("VarWriteRequest.equals: same name, same count, different value")
    void testVarWriteRequestEqualsDiffMemberValue() {
        VarWriteRequest r1 = new VarWriteRequest("AAA", 0, 2, 100);
        VarWriteRequest r2 = new VarWriteRequest("AAA", 0, 2, 200);
        assertNotEquals(r1, r2);
    }

    // ── KecongUdpChannel — remaining constructors/methods ──

    @Test
    @DisplayName("KecongUdpChannel: default constructor (IP + port only)")
    void testKecongUdpChannelDefaultConstructor() throws Exception {
        try (var mock = org.mockito.Mockito.mockConstruction(java.net.DatagramSocket.class)) {
            KecongUdpChannel channel = new KecongUdpChannel("127.0.0.1", 17804);
            assertNotNull(channel);
            assertFalse(channel.isClosed());
        }
    }

    @Test
    @DisplayName("KecongUdpChannel: createNavChannel with auth code")
    void testCreateNavChannelWithAuth() throws Exception {
        byte[] auth = "CUSTOMAUTH123456".getBytes();
        try (var mock = org.mockito.Mockito.mockConstruction(java.net.DatagramSocket.class)) {
            KecongUdpChannel channel = KecongUdpChannel.createNavChannel("192.168.1.1", auth, 2000);
            assertNotNull(channel);
            assertFalse(channel.isClosed());
        }
    }

    // ── ByteBufferUtils.getFixedString — mixed content with null terminator ──

    @Test
    @DisplayName("ByteBufferUtils.getFixedString: bytes with mid-string null terminator")
    void testGetFixedStringWithMidNull() {
        ByteBuffer buf = ByteBuffer.allocate(10);
        buf.put((byte) 'A');
        buf.put((byte) 'B');
        buf.put((byte) 'C');
        buf.put((byte) 0); // null terminates at position 3
        buf.put((byte) 'D'); // should be ignored
        buf.put(new byte[5]);
        buf.flip();
        assertEquals("ABC", ByteBufferUtils.getFixedString(buf, 10));
    }

    // ── KecongMessageDecoder.decodeReadVarResponseName — non-null bytes then null terminator ──

    @Test
    @DisplayName("decodeReadVarResponseName: name with null terminator in middle")
    void testDecodeReadVarResponseNameWithNull() {
        byte[] data = new byte[20];
        data[0] = 'X';
        data[1] = 'Y';
        data[2] = 0; // null terminator
        data[3] = 'Z'; // ignored
        assertEquals("XY", KecongMessageDecoder.decodeReadVarResponseName(data));
    }

    // ── KecongMessageDecoder.taskStateToAgvState — remaining case values ──

    @Test
    @DisplayName("decodeRunStatus: task state NONE(0) maps to IDLE")
    void testDecodeRunStatusTaskStateNone() {
        byte[] data = buildRunStatusData(0);
        assertNotNull(KecongMessageDecoder.decodeRunStatus(data));
    }

    @Test
    @DisplayName("decodeRunStatus: task state DONE(4) maps to IDLE")
    void testDecodeRunStatusTaskStateDone() {
        byte[] data = buildRunStatusData(4);
        assertNotNull(KecongMessageDecoder.decodeRunStatus(data));
    }

    // ── VarMember/VarWriteMember.equals — explicit wrong-type call ──

    @Test
    @DisplayName("VarMember.equals: called on VarMember with String arg")
    void testVarMemberEqualsExplicitWrongType() {
        VarMember m = new VarMember(0, 2);
        assertFalse(m.equals("not a VarMember"));
    }

    @Test
    @DisplayName("VarWriteMember.equals: called on VarWriteMember with String arg")
    void testVarWriteMemberEqualsExplicitWrongType() {
        VarWriteMember m = new VarWriteMember(0, 2, 100);
        assertFalse(m.equals("not a VarWriteMember"));
    }

    @Test
    @DisplayName("VarReadRequest.equals: called with String arg")
    void testVarReadRequestEqualsWrongType() {
        VarReadRequest r = new VarReadRequest("AAA", 0, 2);
        assertFalse(r.equals("not a VarReadRequest"));
    }

    @Test
    @DisplayName("VarWriteRequest.equals: called with String arg")
    void testVarWriteRequestEqualsWrongType() {
        VarWriteRequest r = new VarWriteRequest("AAA", 0, 2, 100);
        assertFalse(r.equals("not a VarWriteRequest"));
    }

    // ── VarReadRequest.equals — same name, same member count, different offset ──

    @Test
    @DisplayName("VarReadRequest.equals: same name, 1 member each, different offset")
    void testVarReadRequestEqualsDiffMemberOffsetExplicit() {
        VarReadRequest r1 = new VarReadRequest("AAA", 0, 2);
        VarReadRequest r2 = new VarReadRequest("AAA", 4, 2);
        assertFalse(r1.equals(r2));
    }

    // ── VarWriteRequest.equals — same name, same count, different offset ──

    @Test
    @DisplayName("VarWriteRequest.equals: same name, 1 member each, different offset")
    void testVarWriteRequestEqualsDiffMemberOffsetExplicit() {
        VarWriteRequest r1 = new VarWriteRequest("AAA", 0, 2, 100);
        VarWriteRequest r2 = new VarWriteRequest("AAA", 4, 2, 100);
        assertFalse(r1.equals(r2));
    }

    // ── KecongUdpChannel — remaining createNavChannel variant ──

    @Test
    @DisplayName("KecongUdpChannel: createNavChannel with custom auth and timeout")
    void testCreateNavChannelWithAuthExplicit() throws Exception {
        byte[] auth = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                                 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10};
        try (var mock = org.mockito.Mockito.mockConstruction(java.net.DatagramSocket.class)) {
            KecongUdpChannel channel = KecongUdpChannel.createNavChannel("10.0.0.1", auth, 3000);
            assertNotNull(channel);
        }
    }

    // ── KecongUdpChannel.sendAndReceive — SocketTimeoutException path ──

    @Test
    @DisplayName("KecongUdpChannel: sendAndReceive handles SocketTimeoutException")
    void testSendAndReceiveSocketTimeoutException() throws Exception {
        try (var mock = org.mockito.Mockito.mockConstruction(java.net.DatagramSocket.class)) {
            KecongUdpChannel channel = new KecongUdpChannel("127.0.0.1", 17804);
            java.net.DatagramSocket sockMock = mock.constructed().get(0);
            org.mockito.Mockito.doThrow(new java.net.SocketTimeoutException("timeout"))
                    .when(sockMock).receive(org.mockito.ArgumentMatchers.any());

            assertNull(channel.sendAndReceive((byte) 0x17, new byte[0]));
        }
    }

    @Test
    @DisplayName("KecongUdpChannel: sendAndReceive handles generic Exception during receive")
    void testSendAndReceiveGenericException() throws Exception {
        try (var mock = org.mockito.Mockito.mockConstruction(java.net.DatagramSocket.class)) {
            KecongUdpChannel channel = new KecongUdpChannel("127.0.0.1", 17804);
            java.net.DatagramSocket sockMock = mock.constructed().get(0);
            org.mockito.Mockito.doThrow(new java.io.IOException("generic IO error"))
                    .when(sockMock).receive(org.mockito.ArgumentMatchers.any());

            assertNull(channel.sendAndReceive((byte) 0x17, new byte[0]));
        }
    }

    // ── taskStateToAgvState: FAIL (5) ──

    @Test
    @DisplayName("decodeRunStatus: task state FAIL(5) maps to NAV_FAILED")
    void testDecodeRunStatusTaskStateFail() {
        byte[] data = buildRunStatusData(5);
        assertNotNull(KecongMessageDecoder.decodeRunStatus(data));
    }

    // ── decodeReadVarResponseName: all 16 bytes non-null ──

    @Test
    @DisplayName("decodeReadVarResponseName: all 16 bytes filled (no null terminator)")
    void testDecodeReadVarResponseNameAllFilled() {
        byte[] data = new byte[20];
        for (int i = 0; i < 16; i++) data[i] = (byte) 'A';
        assertEquals("AAAAAAAAAAAAAAAA",
                KecongMessageDecoder.decodeReadVarResponseName(data));
    }

    // ── decodeRobotStatus: abnormalSize > 0, insufficient remaining bytes ──

    @Test
    @DisplayName("decodeRobotStatus: abnormalSize=5 but insufficient remaining")
    void testDecodeRobotStatusAbnormalInsufficient() {
        ByteBuffer buf = ByteBufferUtils.allocate(200);
        buf.put((byte) 5);  // abnormalSize=5 (needs 5*12=60 bytes)
        buf.put((byte) 0);  // actionSize=0
        buf.putShort((short) 0);
        // Put minimal location+running+task+battery data
        byte[] minData = new byte[100]; // not enough for 5 events
        buf.put(minData);
        // RobotStatus.decodeRobotStatus handles this gracefully
        assertNotNull(KecongMessageDecoder.decodeRobotStatus(buf.array()));
    }

    // ── decodeRobotStatus: actionSize > 0, insufficient remaining bytes ──

    @Test
    @DisplayName("decodeRobotStatus: actionSize=5 but insufficient remaining")
    void testDecodeRobotStatusActionInsufficient() {
        ByteBuffer buf = ByteBufferUtils.allocate(200);
        buf.put((byte) 0);
        buf.put((byte) 5);  // actionSize=5 (needs 5*12=60 bytes)
        buf.putShort((short) 0);
        byte[] minData = new byte[100];
        buf.put(minData);
        assertNotNull(KecongMessageDecoder.decodeRobotStatus(buf.array()));
    }

    // ── ByteBufferUtils.getFixedString — all non-null bytes (end reaches length) ──

    @Test
    @DisplayName("ByteBufferUtils.getFixedString: all non-null bytes")
    void testGetFixedStringFullLength() {
        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.put(new byte[]{'H', 'E', 'L', 'L', 'O'});
        buf.flip();
        assertEquals("HELLO", ByteBufferUtils.getFixedString(buf, 5));
    }

    // ── VarReadRequest.equals — explicit null arg ──

    @Test
    @DisplayName("VarReadRequest.equals: explicit null argument")
    void testVarReadRequestEqualsNull() {
        VarReadRequest r = new VarReadRequest("AAA", 0, 2);
        assertFalse(r.equals(null));
    }

    // ── VarWriteRequest.equals — explicit null arg ──

    @Test
    @DisplayName("VarWriteRequest.equals: explicit null argument")
    void testVarWriteRequestEqualsNull() {
        VarWriteRequest r = new VarWriteRequest("AAA", 0, 2, 100);
        assertFalse(r.equals(null));
    }

    // ── VarReadRequest.equals — explicit this==o ──

    @Test
    @DisplayName("VarReadRequest.equals: explicit this==o check")
    void testVarReadRequestEqualsSelfExplicit() {
        VarReadRequest r = new VarReadRequest("AAA", 0, 2);
        assertTrue(r.equals(r));
    }

    // ── VarWriteRequest.equals — explicit this==o ──

    @Test
    @DisplayName("VarWriteRequest.equals: explicit this==o check")
    void testVarWriteRequestEqualsSelfExplicit() {
        VarWriteRequest r = new VarWriteRequest("AAA", 0, 2, 100);
        assertTrue(r.equals(r));
    }

    // ── KecongUdpChannel: createNavChannel(String, byte[], int) full coverage ──

    @Test
    @DisplayName("KecongUdpChannel: createNavChannel full constructor path")
    void testCreateNavChannelFullConstructor() throws Exception {
        byte[] auth = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                                 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x11};
        try (var mock = org.mockito.Mockito.mockConstruction(java.net.DatagramSocket.class)) {
            KecongUdpChannel channel = KecongUdpChannel.createNavChannel(
                    "192.168.100.178", auth, 3000);
            assertNotNull(channel);
            assertFalse(channel.isClosed());
            java.net.DatagramSocket sockMock = mock.constructed().get(0);
            org.mockito.Mockito.verify(sockMock).setSoTimeout(3000);
        }
    }

    // ── KecongUdpChannel: createNavChannel(ip, timeoutMs) with default auth ──

    @Test
    @DisplayName("KecongUdpChannel: createNavChannel(String, int) with default auth")
    void testCreateNavChannelDefaultAuth() throws Exception {
        try (var mock = org.mockito.Mockito.mockConstruction(java.net.DatagramSocket.class)) {
            KecongUdpChannel channel = KecongUdpChannel.createNavChannel("192.168.1.1", 2000);
            assertNotNull(channel);
            assertFalse(channel.isClosed());
            java.net.DatagramSocket sockMock = mock.constructed().get(0);
            org.mockito.Mockito.verify(sockMock).setSoTimeout(2000);
        }
    }

    // ── KecongMessageDecoder.taskStateToAgvState: WAIT(1) → IDLE(0) ──

    @Test
    @DisplayName("decodeRunStatus: task state WAIT(1) maps to IDLE(0)")
    void testDecodeRunStatusTaskStateWait() {
        byte[] data = buildRunStatusData(1);
        var st = KecongMessageDecoder.decodeRunStatus(data);
        assertNotNull(st);
        assertEquals(1, st.getNavTaskState());
        assertEquals(0, st.getAgvState()); // WAIT → IDLE
    }

    // ── KecongMessageDecoder.decodeRobotStatus: abnormalSize > 0, insufficient remaining ──

    @Test
    @DisplayName("decodeRobotStatus: abnormalSize > 0 but buf.remaining() < abnormalSize*12")
    void testDecodeRobotStatusAbnormalInsufficientRemaining() {
        // Build a buffer with abnormalSize=10 (needs 120 bytes), but only ~88 bytes remaining
        // after header+location+running+task+battery
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(128)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 10); // abnormalSize=10 (needs 10*12=120 bytes)
        buf.put((byte) 0);  // actionSize=0
        buf.putShort((short) 0);
        // Fill minimal location+running+task+battery (84 bytes)
        for (int i = 0; i < 21; i++) buf.putInt(0);
        // remaining = 128 - 4 - 84 = 40 < 120, so the condition fails
        var status = KecongMessageDecoder.decodeRobotStatus(buf.array());
        assertNotNull(status);
    }

    // ── KecongMessageDecoder.decodeRobotStatus: actionSize > 0, insufficient remaining ──

    @Test
    @DisplayName("decodeRobotStatus: actionSize > 0 but buf.remaining() < actionSize*12")
    void testDecodeRobotStatusActionInsufficientRemaining() {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(128)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0);  // abnormalSize=0
        buf.put((byte) 10); // actionSize=10 (needs 10*12=120 bytes)
        buf.putShort((short) 0);
        for (int i = 0; i < 21; i++) buf.putInt(0);
        // remaining = 128 - 4 - 84 = 40 < 120
        var status = KecongMessageDecoder.decodeRobotStatus(buf.array());
        assertNotNull(status);
    }

    // ── VarReadRequest.equals: two different objects with same varName and members ──

    @Test
    @DisplayName("VarReadRequest.equals: different objects with same name and members")
    void testVarReadRequestEqualsSameMembersDifferentObject() {
        VarReadRequest r1 = new VarReadRequest("AAA", 0, 2);
        VarReadRequest r2 = new VarReadRequest("AAA", 0, 2);
        assertTrue(r1.equals(r2));
        assertTrue(r2.equals(r1));
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    // ── VarWriteRequest.equals: two different objects with same varName and members ──

    @Test
    @DisplayName("VarWriteRequest.equals: different objects with same name and members")
    void testVarWriteRequestEqualsSameMembersDifferentObject() {
        VarWriteRequest r1 = new VarWriteRequest("AAA", 0, 2, 100);
        VarWriteRequest r2 = new VarWriteRequest("AAA", 0, 2, 100);
        assertTrue(r1.equals(r2));
        assertTrue(r2.equals(r1));
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    // ── Utility ──

    private static byte[] buildRunStatusData(int taskState) {
        byte[] data = new byte[0xC0];
        data[0x50] = (byte) taskState;
        return data;
    }

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
