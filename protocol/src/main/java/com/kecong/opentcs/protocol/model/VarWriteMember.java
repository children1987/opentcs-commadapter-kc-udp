package com.kecong.opentcs.protocol.model;

import java.util.Objects;

/**
 * A single member to write within a variable, used with 0x03 WRITE_MULTI_VAR.
 *
 * <p>
 * Each member specifies an offset, length, and value. The value is always
 * encoded as U32 (4 bytes) in the protocol, regardless of the actual member length.
 * </p>
 *
 * <p>Verified against real MRC controller (2026-06-10).</p>
 */
public class VarWriteMember {

    private final int offset;
    private final int length;  // 1=BYTE, 2=WORD, 4=DWORD
    private final int value;

    /**
     * @param offset byte offset within the variable's data area
     * @param length number of bytes (1/2/4)
     * @param value  the value to write (stored as U32 in protocol)
     */
    public VarWriteMember(int offset, int length, int value) {
        if (length < 1 || length > 4) {
            throw new IllegalArgumentException("length must be 1-4, got: " + length);
        }
        this.offset = offset;
        this.length = length;
        this.value = value;
    }

    public int getOffset() { return offset; }
    public int getLength() { return length; }
    public int getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VarWriteMember)) return false;
        VarWriteMember that = (VarWriteMember) o;
        return offset == that.offset && length == that.length && value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, length, value);
    }

    @Override
    public String toString() {
        return String.format("VarWriteMember{offset=0x%02X, len=%d, val=%d}", offset, length, value);
    }
}
