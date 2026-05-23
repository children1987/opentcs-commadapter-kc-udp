package com.kecong.opentcs.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Kecong UDP protocol frame structure.
 * Based on "科聪控制器UDP接口协议说明书V2.0" Section 4.1.
 *
 * <pre>
 * Offset  Type      Content
 * 0x00    U8[16]    Protocol auth code (协议授权码)
 * 0x10    U8        Protocol version (协议版本号, fixed 0x01)
 * 0x11    U8        Message type (报文类型): 0x00=request, 0x01=response
 * 0x12    U16       Sequence number (通信序列号, little-endian)
 * 0x14    U8        Service code (服务码, fixed 0x10)
 * 0x15    U8        Command code (命令码)
 * 0x16    U8        Execution code (执行码)
 * 0x17    U8        Reserved (预留, 0)
 * 0x18    U16       Data length (报文数据区长度, little-endian, max 512)
 * 0x1A    U8[2]     Reserved (预留, 0)
 * 0x1C    U8[]      Data payload (报文数据区)
 * </pre>
 */
public class KecongProtocolFrame {

    /** Fixed header size in bytes */
    public static final int HEADER_SIZE = 0x1C;  // 28 bytes

    /** Maximum data payload size in bytes */
    public static final int MAX_DATA_SIZE = 512;

    /** Maximum total frame size */
    public static final int MAX_FRAME_SIZE = HEADER_SIZE + MAX_DATA_SIZE;

    /** Protocol version (fixed) */
    public static final byte PROTOCOL_VERSION = (byte) 0x01;

    /** Service code (fixed) */
    public static final byte SERVICE_CODE = (byte) 0x10;

    /** Message type: request */
    public static final byte MSG_TYPE_REQUEST = (byte) 0x00;

    /** Message type: response */
    public static final byte MSG_TYPE_RESPONSE = (byte) 0x01;

    // Frame fields
    private final byte[] authCode;
    private final byte protocolVersion;
    private final byte messageType;
    private final int sequenceNumber;
    private final byte serviceCode;
    private final byte commandCode;
    private final byte executionCode;
    private final byte[] data;

    private KecongProtocolFrame(Builder builder) {
        this.authCode = builder.authCode.clone();
        this.protocolVersion = builder.protocolVersion;
        this.messageType = builder.messageType;
        this.sequenceNumber = builder.sequenceNumber;
        this.serviceCode = builder.serviceCode;
        this.commandCode = builder.commandCode;
        this.executionCode = builder.executionCode;
        this.data = builder.data.clone();
    }

    public byte[] getAuthCode() { return authCode.clone(); }
    public byte getProtocolVersion() { return protocolVersion; }
    public byte getMessageType() { return messageType; }
    public int getSequenceNumber() { return sequenceNumber; }
    public byte getServiceCode() { return serviceCode; }
    public byte getCommandCode() { return commandCode; }
    public byte getExecutionCode() { return executionCode; }
    public byte[] getData() { return data.clone(); }
    public int getDataLength() { return data.length; }

    public boolean isRequest() { return messageType == MSG_TYPE_REQUEST; }
    public boolean isResponse() { return messageType == MSG_TYPE_RESPONSE; }

    /**
     * Encode this frame into a byte array for UDP transmission.
     */
    public byte[] encode() {
        if (data.length > MAX_DATA_SIZE) {
            throw new IllegalArgumentException("Data length " + data.length + " exceeds max " + MAX_DATA_SIZE);
        }
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + data.length + 4); // +4 safety margin
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(authCode);
        buf.put(protocolVersion);
        buf.put(messageType);
        buf.putShort((short) sequenceNumber);
        buf.put(serviceCode);
        buf.put(commandCode);
        buf.put(executionCode);
        buf.put((byte) 0); // reserved
        buf.putShort((short) data.length);
        buf.putShort((short) 0); // reserved
        buf.put(data);
        return buf.array();
    }

    /**
     * Decode a byte array received via UDP into a KecongProtocolFrame.
     *
     * @param raw  the raw UDP payload bytes
     * @return decoded frame
     * @throws IllegalArgumentException if the frame is malformed
     */
    public static KecongProtocolFrame decode(byte[] raw) {
        if (raw == null || raw.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Frame too short: " + (raw == null ? 0 : raw.length) + " bytes, min " + HEADER_SIZE);
        }
        ByteBuffer buf = ByteBuffer.wrap(raw);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        byte[] authCode = new byte[16];
        buf.get(authCode);
        byte protocolVersion = buf.get();
        byte messageType = buf.get();
        int sequenceNumber = buf.getShort() & 0xFFFF;
        byte serviceCode = buf.get();
        byte commandCode = buf.get();
        byte executionCode = buf.get();
        buf.get(); // reserved
        int dataLength = buf.getShort() & 0xFFFF;
        buf.getShort(); // reserved

        if (dataLength > MAX_DATA_SIZE) {
            throw new IllegalArgumentException("Data length " + dataLength + " exceeds max " + MAX_DATA_SIZE);
        }
        if (raw.length < HEADER_SIZE + dataLength) {
            throw new IllegalArgumentException("Frame truncated: expected " + (HEADER_SIZE + dataLength) + " bytes, got " + raw.length);
        }

        byte[] data = new byte[dataLength];
        buf.get(data);

        return new Builder()
                .authCode(authCode)
                .protocolVersion(protocolVersion)
                .messageType(messageType)
                .sequenceNumber(sequenceNumber)
                .serviceCode(serviceCode)
                .commandCode(commandCode)
                .executionCode(executionCode)
                .data(data)
                .build();
    }

    /**
     * Create a new Builder for constructing frames.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private byte[] authCode = new byte[16];
        private byte protocolVersion = PROTOCOL_VERSION;
        private byte messageType = MSG_TYPE_REQUEST;
        private int sequenceNumber = 0;
        private byte serviceCode = SERVICE_CODE;
        private byte commandCode = 0;
        private byte executionCode = 0;
        private byte[] data = new byte[0];

        public Builder authCode(byte[] authCode) {
            this.authCode = authCode.clone();
            return this;
        }

        public Builder authCode(String authCodeStr) {
            byte[] bytes = authCodeStr.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            this.authCode = new byte[16];
            System.arraycopy(bytes, 0, this.authCode, 0, Math.min(bytes.length, 16));
            return this;
        }

        public Builder protocolVersion(byte v) { this.protocolVersion = v; return this; }
        public Builder messageType(byte t) { this.messageType = t; return this; }
        public Builder sequenceNumber(int seq) { this.sequenceNumber = seq; return this; }
        public Builder serviceCode(byte sc) { this.serviceCode = sc; return this; }
        public Builder commandCode(byte cmd) { this.commandCode = cmd; return this; }
        public Builder executionCode(byte ec) { this.executionCode = ec; return this; }
        public Builder data(byte[] data) { this.data = data.clone(); return this; }

        /**
         * Create a request frame.
         */
        public Builder asRequest() { this.messageType = MSG_TYPE_REQUEST; this.executionCode = 0; return this; }

        /**
         * Create a response frame.
         */
        public Builder asResponse() { this.messageType = MSG_TYPE_RESPONSE; return this; }

        public KecongProtocolFrame build() {
            return new KecongProtocolFrame(this);
        }
    }

    @Override
    public String toString() {
        return String.format("KecongFrame[type=%s, seq=%d, cmd=0x%02X, exec=0x%02X, dataLen=%d]",
                isRequest() ? "REQ" : "RESP",
                sequenceNumber,
                commandCode & 0xFF,
                executionCode & 0xFF,
                data.length);
    }
}
