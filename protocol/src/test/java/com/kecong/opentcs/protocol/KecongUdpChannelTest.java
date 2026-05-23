package com.kecong.opentcs.protocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for KecongUdpChannel using Mockito mockConstruction.
 * Covers all methods, branches, and error paths.
 */
@DisplayName("KecongUdpChannel")
class KecongUdpChannelTest {

    private static final byte[] AUTH_CODE = "TESTAUTH12345678".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private MockedConstruction<DatagramSocket> socketMock;

    @BeforeEach
    void setUp() {
        // Will be set up per test
    }

    @AfterEach
    void tearDown() {
        if (socketMock != null && !socketMock.isClosed()) {
            socketMock.close();
        }
    }

    private DatagramSocket mockSocket() {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        return socketMock.constructed().get(0);
    }

    // ===== Constructor tests =====

    @Test
    @DisplayName("Constructor opens UDP channel successfully")
    void testConstructorSuccess() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        assertNotNull(channel);
        assertFalse(channel.isClosed());
        assertEquals(0, channel.getSequenceNumber());

        DatagramSocket mock = socketMock.constructed().get(0);
        verify(mock).setSoTimeout(500);
        channel.close();
    }

    @Test
    @DisplayName("Constructor with default timeout (1000ms)")
    void testConstructorDefaultTimeout() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE);
        assertNotNull(channel);
        assertFalse(channel.isClosed());

        DatagramSocket mock = socketMock.constructed().get(0);
        verify(mock).setSoTimeout(1000);
        channel.close();
    }

    @Test
    @DisplayName("Constructor throws IOException on unknown host")
    void testConstructorUnknownHost() {
        assertThrows(IOException.class, () ->
                new KecongUdpChannel("invalid..host...name", 17804, AUTH_CODE, 500));
    }

    @Test
    @DisplayName("createNavChannel factory method")
    void testCreateNavChannel() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = KecongUdpChannel.createNavChannel("192.168.1.1", AUTH_CODE, 1000);
        assertNotNull(channel);
        assertFalse(channel.isClosed());
        assertEquals(KecongUdpChannel.DEFAULT_NAV_PORT, 17804);
        channel.close();
    }

    // ===== sendAndReceive tests =====

    @Test
    @DisplayName("sendAndReceive returns response frame on success")
    void testSendAndReceiveSuccess() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        // Prepare a mock response frame
        KecongProtocolFrame responseFrame = KecongProtocolFrame.builder()
                .authCode(AUTH_CODE).asResponse().sequenceNumber(1)
                .commandCode((byte) 0xAF).executionCode((byte) 0x00)
                .data(new byte[0]).build();
        byte[] responseBytes = responseFrame.encode();

        // Mock receive to return the response
        doAnswer(inv -> {
            DatagramPacket p = inv.getArgument(0);
            System.arraycopy(responseBytes, 0, p.getData(), 0, responseBytes.length);
            p.setLength(responseBytes.length);
            return null;
        }).when(mock).receive(any(DatagramPacket.class));

        KecongProtocolFrame result = channel.sendAndReceive((byte) 0xAF, new byte[0]);
        assertNotNull(result);
        assertTrue(result.isResponse());
        assertEquals((byte) 0x00, result.getExecutionCode());

        verify(mock).send(any(DatagramPacket.class));
        verify(mock).receive(any(DatagramPacket.class));
        channel.close();
    }

    @Test
    @DisplayName("sendAndReceive returns null on SocketTimeoutException")
    void testSendAndReceiveTimeout() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        doThrow(new SocketTimeoutException("Timeout")).when(mock).receive(any(DatagramPacket.class));

        KecongProtocolFrame result = channel.sendAndReceive((byte) 0xAF, new byte[0]);
        assertNull(result);
        channel.close();
    }

    @Test
    @DisplayName("sendAndReceive handles null data parameter")
    void testSendAndReceiveNullData() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        KecongProtocolFrame responseFrame = KecongProtocolFrame.builder()
                .authCode(AUTH_CODE).asResponse().sequenceNumber(1)
                .commandCode((byte) 0xAF).executionCode((byte) 0x00)
                .data(new byte[0]).build();
        byte[] responseBytes = responseFrame.encode();

        doAnswer(inv -> {
            DatagramPacket p = inv.getArgument(0);
            System.arraycopy(responseBytes, 0, p.getData(), 0, responseBytes.length);
            p.setLength(responseBytes.length);
            return null;
        }).when(mock).receive(any(DatagramPacket.class));

        KecongProtocolFrame result = channel.sendAndReceive((byte) 0xAF, null);
        assertNotNull(result);
        channel.close();
    }

    @Test
    @DisplayName("sendAndReceive throws IOException when channel closed")
    void testSendAndReceiveClosed() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        channel.close();
        assertThrows(IOException.class, () -> channel.sendAndReceive((byte) 0xAF, new byte[0]));
    }

    // ===== sendAndVerify tests =====

    @Test
    @DisplayName("sendAndVerify returns true on success execution code")
    void testSendAndVerifySuccess() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        KecongProtocolFrame responseFrame = KecongProtocolFrame.builder()
                .authCode(AUTH_CODE).asResponse().sequenceNumber(1)
                .commandCode((byte) 0xAE).executionCode((byte) 0x00)
                .data(new byte[0]).build();
        byte[] responseBytes = responseFrame.encode();

        doAnswer(inv -> {
            DatagramPacket p = inv.getArgument(0);
            System.arraycopy(responseBytes, 0, p.getData(), 0, responseBytes.length);
            p.setLength(responseBytes.length);
            return null;
        }).when(mock).receive(any(DatagramPacket.class));

        assertTrue(channel.sendAndVerify((byte) 0xAE, new byte[0]));
        channel.close();
    }

    @Test
    @DisplayName("sendAndVerify returns false on timeout")
    void testSendAndVerifyTimeout() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        doThrow(new SocketTimeoutException("Timeout")).when(mock).receive(any(DatagramPacket.class));
        assertFalse(channel.sendAndVerify((byte) 0xAE, new byte[0]));
        channel.close();
    }

    @Test
    @DisplayName("sendAndVerify returns false on error execution code")
    void testSendAndVerifyFailure() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        KecongProtocolFrame responseFrame = KecongProtocolFrame.builder()
                .authCode(AUTH_CODE).asResponse().sequenceNumber(1)
                .commandCode((byte) 0xAE).executionCode(KecongExecutionCode.EXEC_AUTH_CODE_ERROR)
                .data(new byte[0]).build();
        byte[] responseBytes = responseFrame.encode();

        doAnswer(inv -> {
            DatagramPacket p = inv.getArgument(0);
            System.arraycopy(responseBytes, 0, p.getData(), 0, responseBytes.length);
            p.setLength(responseBytes.length);
            return null;
        }).when(mock).receive(any(DatagramPacket.class));

        assertFalse(channel.sendAndVerify((byte) 0xAE, new byte[0]));
        channel.close();
    }

    // ===== sendAndGetData tests =====

    @Test
    @DisplayName("sendAndGetData returns data payload on success")
    void testSendAndGetDataSuccess() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        byte[] payload = new byte[]{0x01, 0x02, 0x03};
        KecongProtocolFrame responseFrame = KecongProtocolFrame.builder()
                .authCode(AUTH_CODE).asResponse().sequenceNumber(1)
                .commandCode((byte) 0xAF).executionCode((byte) 0x00)
                .data(payload).build();
        byte[] responseBytes = responseFrame.encode();

        doAnswer(inv -> {
            DatagramPacket p = inv.getArgument(0);
            System.arraycopy(responseBytes, 0, p.getData(), 0, responseBytes.length);
            p.setLength(responseBytes.length);
            return null;
        }).when(mock).receive(any(DatagramPacket.class));

        byte[] result = channel.sendAndGetData((byte) 0xAF, new byte[0]);
        assertNotNull(result);
        assertArrayEquals(payload, result);
        channel.close();
    }

    @Test
    @DisplayName("sendAndGetData returns null on timeout")
    void testSendAndGetDataTimeout() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        doThrow(new SocketTimeoutException("Timeout")).when(mock).receive(any(DatagramPacket.class));
        assertNull(channel.sendAndGetData((byte) 0xAF, new byte[0]));
        channel.close();
    }

    @Test
    @DisplayName("sendAndGetData returns null on execution error")
    void testSendAndGetDataError() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        DatagramSocket mock = socketMock.constructed().get(0);

        KecongProtocolFrame responseFrame = KecongProtocolFrame.builder()
                .authCode(AUTH_CODE).asResponse().sequenceNumber(1)
                .commandCode((byte) 0xAF).executionCode(KecongExecutionCode.EXEC_FAILED_UNKNOWN)
                .data(new byte[0]).build();
        byte[] responseBytes = responseFrame.encode();

        doAnswer(inv -> {
            DatagramPacket p = inv.getArgument(0);
            System.arraycopy(responseBytes, 0, p.getData(), 0, responseBytes.length);
            p.setLength(responseBytes.length);
            return null;
        }).when(mock).receive(any(DatagramPacket.class));

        assertNull(channel.sendAndGetData((byte) 0xAF, new byte[0]));
        channel.close();
    }

    // ===== close / isClosed tests =====

    @Test
    @DisplayName("close closes the socket and marks channel as closed")
    void testClose() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        assertFalse(channel.isClosed());

        channel.close();
        assertTrue(channel.isClosed());

        DatagramSocket mock = socketMock.constructed().get(0);
        verify(mock).close();
    }

    @Test
    @DisplayName("Double close is safe (idempotent)")
    void testDoubleClose() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        channel.close();
        channel.close(); // second close should not throw
        assertTrue(channel.isClosed());

        DatagramSocket mock = socketMock.constructed().get(0);
        verify(mock, times(1)).close(); // only called once
    }

    // ===== Sequence number tests =====

    @Test
    @DisplayName("getSequenceNumber returns current sequence")
    void testGetSequenceNumber() throws Exception {
        socketMock = Mockito.mockConstruction(DatagramSocket.class);
        KecongUdpChannel channel = new KecongUdpChannel("192.168.1.1", 17804, AUTH_CODE, 500);
        assertEquals(0, channel.getSequenceNumber());

        // After one send, sequence should be 1
        DatagramSocket mock = socketMock.constructed().get(0);
        doThrow(new SocketTimeoutException("Timeout")).when(mock).receive(any(DatagramPacket.class));

        channel.sendAndReceive((byte) 0xAF, new byte[0]);
        assertEquals(1, channel.getSequenceNumber());

        channel.sendAndReceive((byte) 0xAF, new byte[0]);
        assertEquals(2, channel.getSequenceNumber());
        channel.close();
    }

    // ===== Default constants =====

    @Test
    @DisplayName("Default port constants are correct")
    void testDefaultPorts() {
        assertEquals(17804, KecongUdpChannel.DEFAULT_NAV_PORT);
        assertEquals(17800, KecongUdpChannel.DEFAULT_QR_PORT);
        assertEquals("192.168.100.178", KecongUdpChannel.DEFAULT_CONTROLLER_IP);
    }
}
