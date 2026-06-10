package com.kecong.opentcs.protocol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Request model for the 0x03 WRITE_MULTI_VAR command.
 *
 * <p>
 * Writes one or more member values to a controller variable area.
 * </p>
 *
 * <h3>Protocol format (verified on real MRC controller)</h3>
 * <pre>
 * Request:  [U32 count][StrValue × N]  — NO ValueID (differs from 0x02)
 * StrValue: [U8×16 name][U32 memberCount][ValueMember × M]
 * ValueMember: [U16 offset][U16 length][U32 value]
 * Response: no data payload
 * </pre>
 *
 * <p>Verified 2026-06-10: AAA[0:2]=333, AAA[2:2]=334 on real MRC controller.</p>
 *
 * @see VarWriteMember
 */
public class VarWriteRequest {

    private final String varName;
    private final List<VarWriteMember> members;

    /**
     * Create a write request for a single member.
     */
    public VarWriteRequest(String varName, int offset, int length, int value) {
        this(varName, Collections.singletonList(new VarWriteMember(offset, length, value)));
    }

    /**
     * Create a write request for multiple members.
     */
    public VarWriteRequest(String varName, List<VarWriteMember> members) {
        this.varName = Objects.requireNonNull(varName, "varName");
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
        if (members.isEmpty()) {
            throw new IllegalArgumentException("members must not be empty");
        }
    }

    public String getVarName() { return varName; }
    public List<VarWriteMember> getMembers() { return Collections.unmodifiableList(members); }

    /**
     * Calculate the encoded size of this request's StrValue.
     */
    public int getStrValueSize() {
        return 16                     // name (U8[16])
                + 4                    // member count (U32)
                + members.size() * 8;  // ValueMember (U16+U16+U32) each
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VarWriteRequest)) return false;
        VarWriteRequest that = (VarWriteRequest) o;
        return varName.equals(that.varName) && members.equals(that.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(varName, members);
    }

    @Override
    public String toString() {
        return String.format("VarWriteRequest{name='%s', members=%s}", varName, members);
    }
}
