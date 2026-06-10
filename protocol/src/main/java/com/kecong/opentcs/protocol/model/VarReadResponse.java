package com.kecong.opentcs.protocol.model;

import com.kecong.opentcs.util.ByteBufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Response model for the 0x02 READ_MULTI_VAR command.
 *
 * <p>
 * Parses the controller's response and provides typed access to the returned values.
 * Values are stored 4-byte aligned in the order they were requested.
 * </p>
 *
 * <h3>Response format (verified on real MRC controller)</h3>
 * <pre>
 * Offset  Type   Content
 * 0x00    U32    ValueID (echo of request)
 * 0x04    U32    Total data length (0 = request format error)  — NOTE: U32, not U16!
 * 0x08    U8[]   Variable values (4-byte aligned, compact)
 * </pre>
 *
 * <h3>Verified with real controller</h3>
 * <p>
 * 2026-06-10: Reading B2GW[0x18:4] returned 127080 (0x0001F068) on real MRC controller.
 * </p>
 *
 * @see VarReadRequest
 */
public class VarReadResponse {

    private final int valueId;
    private final int dataLength;
    private final byte[] values;

    /**
     * Decode from raw response data payload.
     *
     * @param data the response data from the 0x02 command
     * @return decoded response, or null if data is too short
     */
    public static VarReadResponse decode(byte[] data) {
        if (data == null || data.length < 8) {
            return null;
        }
        ByteBuffer buf = ByteBufferUtils.wrap(data);
        int valueId = buf.getInt();
        int dataLen = buf.getInt();  // U32, not U16!
        byte[] values = new byte[Math.min(dataLen, data.length - 8)];
        buf.get(values);
        return new VarReadResponse(valueId, dataLen, values);
    }

    private VarReadResponse(int valueId, int dataLength, byte[] values) {
        this.valueId = valueId;
        this.dataLength = dataLength;
        this.values = values.clone();
    }

    public int getValueId() { return valueId; }

    /**
     * Total length of the values section as reported by the controller.
     */
    public int getDataLength() { return dataLength; }

    /**
     * Raw values bytes.
     */
    public byte[] getValues() { return values.clone(); }

    /**
     * Get a signed 32-bit integer at the given byte offset within the values area.
     */
    public int getInt(int offset) {
        if (offset < 0 || offset + 4 > values.length) {
            throw new IndexOutOfBoundsException(
                    String.format("offset %d + 4 > values length %d", offset, values.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(values).order(ByteOrder.LITTLE_ENDIAN);
        return buf.getInt(offset);
    }

    /**
     * Get an unsigned 16-bit integer at the given byte offset.
     */
    public int getUnsignedShort(int offset) {
        if (offset < 0 || offset + 2 > values.length) {
            throw new IndexOutOfBoundsException(
                    String.format("offset %d + 2 > values length %d", offset, values.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(values).order(ByteOrder.LITTLE_ENDIAN);
        return buf.getShort(offset) & 0xFFFF;
    }

    /**
     * Get a single byte at the given offset.
     */
    public int getByte(int offset) {
        if (offset < 0 || offset >= values.length) {
            throw new IndexOutOfBoundsException(
                    String.format("offset %d >= values length %d", offset, values.length));
        }
        return values[offset] & 0xFF;
    }

    @Override
    public String toString() {
        StringBuilder hex = new StringBuilder();
        for (byte b : values) {
            hex.append(String.format("%02X", b & 0xFF));
        }
        return String.format("VarReadResponse{valueId=%d, dataLen=%d, values=[%s]}",
                valueId, dataLength, hex.toString());
    }
}
