package com.kecong.opentcs.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * UDP communication channel for Kecong protocol.
 * Handles sending requests and receiving responses with sequence number management.
 *
 * <p>Communication parameters:
 * <ul>
 *   <li>Navigation port: 17804 (laser/hybrid navigation)</li>
 *   <li>Variable port: 17800 (variable operations, QR code navigation)</li>
 *   <li>Default controller IP (direct connect): 192.168.100.178 (laser), 192.168.100.200 (QR)</li>
 *   <li>Recommended polling interval: 100ms</li>
 *   <li>Request-response pattern: controller only responds, never initiates</li>
 * </ul>
 */
public class KecongUdpChannel implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KecongUdpChannel.class);

    /** Default navigation port */
    public static final int DEFAULT_NAV_PORT = 17804;
    /** Default variable operations port */
    public static final int DEFAULT_QR_PORT = 17800;
    /** Default controller IP (laser navigation → algorithm unit, direct connect) */
    public static final String DEFAULT_CONTROLLER_IP = "192.168.100.178";
    /** Default QR/magnetic controller IP (→ logic unit) */
    public static final String DEFAULT_QR_HOST = "192.168.100.200";
    /** Default protocol auth code (16 bytes, confirmed fixed across all Kecong controllers) */
    public static final byte[] DEFAULT_AUTH_CODE = new byte[]{
            (byte) 0xed, (byte) 0x01, (byte) 0xe9, (byte) 0xd2,
            (byte) 0xb8, (byte) 0xa2, (byte) 0x6b, (byte) 0x4c,
            (byte) 0x85, (byte) 0x72, (byte) 0x77, (byte) 0xf2,
            (byte) 0xb2, (byte) 0xcb, (byte) 0x61, (byte) 0xb4
    };

    private final InetAddress controllerAddress;
    private final int controllerPort;
    private final byte[] authCode;
    private final DatagramSocket socket;
    private final AtomicInteger sequenceNumber;
    private final ReentrantLock sendLock;
    private final int timeoutMs;
    private volatile boolean closed;

    /**
     * Create a new UDP channel.
     *
     * @param controllerIp   controller IP address
     * @param controllerPort controller port (17804 for nav, 17800 for variables)
     * @param authCode       protocol auth code (16 bytes, from Kecong sales)
     * @param timeoutMs      socket timeout in milliseconds
     * @throws IOException if socket creation fails
     */
    public KecongUdpChannel(String controllerIp, int controllerPort, byte[] authCode, int timeoutMs) throws IOException {
        this.controllerAddress = InetAddress.getByName(controllerIp);
        this.controllerPort = controllerPort;
        this.authCode = authCode.clone();
        this.timeoutMs = timeoutMs;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(timeoutMs);
        this.sequenceNumber = new AtomicInteger(0);
        this.sendLock = new ReentrantLock();
        this.closed = false;
        LOG.info("KecongUdpChannel opened: {}:{}, timeout={}ms", controllerIp, controllerPort, timeoutMs);
    }

    /**
     * Create with default timeout (1000ms) and provided auth code.
     */
    public KecongUdpChannel(String controllerIp, int controllerPort, byte[] authCode) throws IOException {
        this(controllerIp, controllerPort, authCode, 1000);
    }

    /**
     * Create with default auth code and specified timeout.
     */
    public KecongUdpChannel(String controllerIp, int controllerPort) throws IOException {
        this(controllerIp, controllerPort, DEFAULT_AUTH_CODE, 1000);
    }

    /**
     * Create navigation channel with default port (17804).
     */
    public static KecongUdpChannel createNavChannel(String controllerIp, byte[] authCode, int timeoutMs) throws IOException {
        return new KecongUdpChannel(controllerIp, DEFAULT_NAV_PORT, authCode, timeoutMs);
    }

    /**
     * Create navigation channel with default port (17804) and default auth code.
     */
    public static KecongUdpChannel createNavChannel(String controllerIp, int timeoutMs) throws IOException {
        return new KecongUdpChannel(controllerIp, DEFAULT_NAV_PORT, DEFAULT_AUTH_CODE, timeoutMs);
    }

    /**
     * Send a request frame and receive the response.
     *
     * @param commandCode  command code
     * @param data         data payload (can be empty)
     * @return response frame, or null on timeout
     * @throws IOException on I/O error
     */
    public KecongProtocolFrame sendAndReceive(byte commandCode, byte[] data) throws IOException {
        if (closed) {
            throw new IOException("Channel is closed");
        }

        int seq = sequenceNumber.incrementAndGet() & 0xFFFF;

        KecongProtocolFrame request = KecongProtocolFrame.builder()
                .authCode(authCode)
                .asRequest()
                .sequenceNumber(seq)
                .commandCode(commandCode)
                .data(data != null ? data : new byte[0])
                .build();

        byte[] requestBytes = request.encode();

        sendLock.lock();
        try {
            // Send
            DatagramPacket sendPacket = new DatagramPacket(requestBytes, requestBytes.length, controllerAddress, controllerPort);
            socket.send(sendPacket);
            LOG.trace("Sent: {}", request);

            // Receive
            byte[] recvBuf = new byte[KecongProtocolFrame.MAX_FRAME_SIZE];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
            try {
                socket.receive(recvPacket);
                byte[] responseData = new byte[recvPacket.getLength()];
                System.arraycopy(recvPacket.getData(), 0, responseData, 0, recvPacket.getLength());

                KecongProtocolFrame response = KecongProtocolFrame.decode(responseData);
                LOG.trace("Received: {}", response);
                return response;
            } catch (SocketTimeoutException e) {
                LOG.debug("Timeout waiting for response to cmd=0x{:02X} seq={}", commandCode & 0xFF, seq);
                return null;
            }
        } finally {
            sendLock.unlock();
        }
    }

    /**
     * Send a request and verify execution success.
     *
     * @return true if response received with EXEC_SUCCESS
     * @throws IOException on I/O error
     */
    public boolean sendAndVerify(byte commandCode, byte[] data) throws IOException {
        KecongProtocolFrame response = sendAndReceive(commandCode, data);
        if (response == null) {
            return false;
        }
        return KecongExecutionCode.isSuccess(response.getExecutionCode());
    }

    /**
     * Send a request and get just the data payload from the response.
     *
     * @return data payload, or null on timeout/failure
     * @throws IOException on I/O error
     */
    public byte[] sendAndGetData(byte commandCode, byte[] data) throws IOException {
        KecongProtocolFrame response = sendAndReceive(commandCode, data);
        if (response == null || !KecongExecutionCode.isSuccess(response.getExecutionCode())) {
            return null;
        }
        return response.getData();
    }

    public int getSequenceNumber() {
        return sequenceNumber.get();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            socket.close();
            LOG.info("KecongUdpChannel closed: {}:{}", controllerAddress.getHostAddress(), controllerPort);
        }
    }
}
