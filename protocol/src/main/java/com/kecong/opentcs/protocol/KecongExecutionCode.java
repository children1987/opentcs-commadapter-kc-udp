package com.kecong.opentcs.protocol;

/**
 * Kecong protocol execution codes (执行码).
 * Based on "科聪控制器UDP接口协议说明书V2.0" Section 4.3.
 */
public final class KecongExecutionCode {

    private KecongExecutionCode() {}

    /** Successful execution */
    public static final byte EXEC_SUCCESS = (byte) 0x00;
    /** Execution failed, unknown reason */
    public static final byte EXEC_FAILED_UNKNOWN = (byte) 0x01;
    /** Service code error */
    public static final byte EXEC_SERVICE_CODE_ERROR = (byte) 0x02;
    /** Command code error */
    public static final byte EXEC_COMMAND_CODE_ERROR = (byte) 0x03;
    /** Header error */
    public static final byte EXEC_HEADER_ERROR = (byte) 0x04;
    /** Message length error */
    public static final byte EXEC_LENGTH_ERROR = (byte) 0x05;
    /** Path point count exceeded limit */
    public static final byte EXEC_POINT_COUNT_EXCEEDED = (byte) 0x83;
    /** Path splice offset mismatch */
    public static final byte EXEC_SPLICE_OFFSET_MISMATCH = (byte) 0x84;
    /** Splice path sequence number mismatch */
    public static final byte EXEC_SPLICE_SEQ_MISMATCH = (byte) 0x85;
    /** Splice task sequence number mismatch */
    public static final byte EXEC_SPLICE_TASK_SEQ_MISMATCH = (byte) 0x86;
    /** Task splice exceeded max task count */
    public static final byte EXEC_SPLICE_MAX_EXCEEDED = (byte) 0x87;
    /** Cannot execute - vehicle nav state conflict */
    public static final byte EXEC_NAV_STATE_CONFLICT = (byte) 0x80;
    /** Protocol auth code error */
    public static final byte EXEC_AUTH_CODE_ERROR = (byte) 0xFF;

    /**
     * Check if execution was successful.
     */
    public static boolean isSuccess(byte code) {
        return code == EXEC_SUCCESS;
    }

    /**
     * Get human-readable description for an execution code.
     */
    public static String describe(byte code) {
        switch (code) {
            case EXEC_SUCCESS: return "Success";
            case EXEC_FAILED_UNKNOWN: return "Failed (unknown)";
            case EXEC_SERVICE_CODE_ERROR: return "Service code error";
            case EXEC_COMMAND_CODE_ERROR: return "Command code error";
            case EXEC_HEADER_ERROR: return "Header error";
            case EXEC_LENGTH_ERROR: return "Length error";
            case EXEC_POINT_COUNT_EXCEEDED: return "Path point count exceeded";
            case EXEC_SPLICE_OFFSET_MISMATCH: return "Splice offset mismatch";
            case EXEC_SPLICE_SEQ_MISMATCH: return "Splice sequence mismatch";
            case EXEC_SPLICE_TASK_SEQ_MISMATCH: return "Splice task sequence mismatch";
            case EXEC_SPLICE_MAX_EXCEEDED: return "Splice max task exceeded";
            case EXEC_NAV_STATE_CONFLICT: return "Nav state conflict";
            case EXEC_AUTH_CODE_ERROR: return "Auth code error";
            default: return "Unknown (0x" + Integer.toHexString(code & 0xFF) + ")";
        }
    }
}
