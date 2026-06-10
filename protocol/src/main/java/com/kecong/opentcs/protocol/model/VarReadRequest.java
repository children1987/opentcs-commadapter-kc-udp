package com.kecong.opentcs.protocol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Request model for the 0x02 READ_MULTI_VAR command.
 *
 * <p>
 * Reads one or more members from a controller variable area (e.g., B2GW).
 * Each variable can have multiple (offset, length) member specifications.
 * </p>
 *
 * <h3>Protocol reference</h3>
 * <p>
 * Based on "科聪控制器UDP接口协议说明书V2.0" Section 5.1.3:
 * </p>
 * <pre>
 * Request: [U8 count][U8×3 reserved][U32 ValueID][StrValue × N]
 * StrValue = [U8×16 name][U32 memberCount][ValueMember × M]
 * ValueMember = [U16 offset][U16 length]
 * </pre>
 *
 * <h3>Verified with real controller</h3>
 * <p>
 * 2026-06-10: B2GW offset 0x18 (DINT) == 127080 verified on real MRC controller.
 * </p>
 *
 * @see VarReadResponse
 */
public class VarReadRequest {

    private final String varName;
    private final List<VarMember> members;

    /**
     * A single member (offset, length) within a variable.
     */
    public static class VarMember {
        private final int offset;
        private final int length; // 1=BYTE, 2=WORD, 4=DWORD

        /**
         * @param offset byte offset within the variable's data area
         * @param length number of bytes to read (1/2/4)
         */
        public VarMember(int offset, int length) {
            if (length < 1 || length > 4) {
                throw new IllegalArgumentException("length must be 1-4, got: " + length);
            }
            this.offset = offset;
            this.length = length;
        }

        public int getOffset() { return offset; }
        public int getLength() { return length; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VarMember)) return false;
            VarMember that = (VarMember) o;
            return offset == that.offset && length == that.length;
        }

        @Override
        public int hashCode() {
            return Objects.hash(offset, length);
        }

        @Override
        public String toString() {
            return String.format("VarMember{offset=0x%02X, length=%d}", offset, length);
        }
    }

    /**
     * Create a request for a single member of a variable.
     *
     * @param varName variable name (e.g., "B2GW")
     * @param offset  byte offset within the variable
     * @param length  byte length (1/2/4)
     */
    public VarReadRequest(String varName, int offset, int length) {
        this(varName, Collections.singletonList(new VarMember(offset, length)));
    }

    /**
     * Create a request for multiple members of a variable.
     *
     * @param varName variable name
     * @param members list of (offset, length) specs
     */
    public VarReadRequest(String varName, List<VarMember> members) {
        this.varName = Objects.requireNonNull(varName, "varName");
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
        if (members.isEmpty()) {
            throw new IllegalArgumentException("members must not be empty");
        }
    }

    public String getVarName() { return varName; }
    public List<VarMember> getMembers() { return Collections.unmodifiableList(members); }

    /**
     * Calculate the encoded size of this request's StrValue.
     */
    public int getStrValueSize() {
        return 16                     // name (U8[16])
                + 4                    // member count (U32)
                + members.size() * 4;  // ValueMember (U16+U16) each
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VarReadRequest)) return false;
        VarReadRequest that = (VarReadRequest) o;
        return varName.equals(that.varName) && members.equals(that.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(varName, members);
    }

    @Override
    public String toString() {
        return String.format("VarReadRequest{name='%s', members=%s}", varName, members);
    }
}
