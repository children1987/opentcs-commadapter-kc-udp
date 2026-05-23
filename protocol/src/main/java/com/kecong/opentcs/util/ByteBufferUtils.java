package com.kecong.opentcs.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility methods for working with little-endian ByteBuffers
 * in the Kecong protocol.
 */
public final class ByteBufferUtils {

    private ByteBufferUtils() {}

    /**
     * Allocate a little-endian ByteBuffer with the given capacity.
     */
    public static ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Wrap a byte array into a little-endian ByteBuffer.
     */
    public static ByteBuffer wrap(byte[] array) {
        return ByteBuffer.wrap(array).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Write a string as ASCII bytes, padding or truncating to fixed length.
     */
    public static void putFixedString(ByteBuffer buf, String str, int length) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int copyLen = Math.min(bytes.length, length);
        buf.put(bytes, 0, copyLen);
        // pad with zeros
        for (int i = copyLen; i < length; i++) {
            buf.put((byte) 0);
        }
    }

    /**
     * Read a fixed-length ASCII string from the buffer.
     */
    public static String getFixedString(ByteBuffer buf, int length) {
        byte[] bytes = new byte[length];
        buf.get(bytes);
        int end = 0;
        while (end < length && bytes[end] != 0) end++;
        return new String(bytes, 0, end, java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * Pad with zeros to align to the given boundary.
     */
    public static void alignTo4(ByteBuffer buf) {
        int pos = buf.position();
        int remainder = pos % 4;
        if (remainder != 0) {
            for (int i = 0; i < 4 - remainder; i++) {
                buf.put((byte) 0);
            }
        }
    }
}
