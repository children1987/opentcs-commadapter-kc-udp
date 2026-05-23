package com.kecong.opentcs.protocol;

/**
 * Kecong UDP protocol command codes (命令码).
 * Based on "科聪控制器UDP接口协议说明书V2.0" Section 4.2.
 */
public final class KecongCommandCode {

    private KecongCommandCode() {}

    // ===== Variable Operations (变量操作服务) =====
    /** Write single variable value */
    public static final byte CMD_WRITE_VAR = (byte) 0x00;
    /** Read single variable value */
    public static final byte CMD_READ_VAR = (byte) 0x01;
    /** Read multiple variable values */
    public static final byte CMD_READ_MULTI_VAR = (byte) 0x02;
    /** Write multiple variable values */
    public static final byte CMD_WRITE_MULTI_VAR = (byte) 0x03;

    // ===== Laser Navigation (激光导航服务) =====
    /** Switch between auto/manual mode */
    public static final byte CMD_AUTO_MANUAL_SWITCH = (byte) 0x11;
    /** Manual positioning */
    public static final byte CMD_MANUAL_POSITION = (byte) 0x14;
    /** Get current position */
    public static final byte CMD_GET_POSITION = (byte) 0x15;
    /** Navigation control */
    public static final byte CMD_NAV_CONTROL = (byte) 0x16;
    /** Query robot running status */
    public static final byte CMD_QUERY_RUN_STATUS = (byte) 0x17;
    /** Query robot navigation status */
    public static final byte CMD_QUERY_NAV_STATUS = (byte) 0x1D;
    /** Confirm robot position */
    public static final byte CMD_CONFIRM_POSITION = (byte) 0x1F;

    // ===== QR Code Navigation (二维码导航服务) =====
    /** QR code task control */
    public static final byte CMD_QR_TASK_CONTROL = (byte) 0xF0;
    /** Dispatch QR code navigation task */
    public static final byte CMD_QR_NAV_TASK = (byte) 0xF1;
    /** Get QR code navigation status */
    public static final byte CMD_QR_NAV_STATUS = (byte) 0xF2;
    /** Dispatch long-path QR code task */
    public static final byte CMD_QR_LONG_PATH_TASK = (byte) 0xF5;
    /** Dispatch long-path QR code task with actions */
    public static final byte CMD_QR_LONG_PATH_ACTION_TASK = (byte) 0xF6;
    /** Dispatch spliceable QR code task with actions */
    public static final byte CMD_QR_SPLICE_ACTION_TASK = (byte) 0xF7;
    /** Get QR code navigation status with segments */
    public static final byte CMD_QR_SEGMENT_STATUS = (byte) 0xF8;

    // ===== Magnetic Navigation (磁导航服务) =====
    /** Dispatch magnetic navigation task */
    public static final byte CMD_MAG_TASK_DISPATCH = (byte) 0xE0;
    /** Magnetic navigation task control */
    public static final byte CMD_MAG_TASK_CONTROL = (byte) 0xE1;
    /** Get magnetic navigation run status */
    public static final byte CMD_MAG_RUN_STATUS = (byte) 0xE2;
    /** Vehicle re-localization */
    public static final byte CMD_MAG_RELOCALIZE = (byte) 0xE3;

    // ===== Scheduling Task Control (调度任务控制) =====
    /** Dispatch hybrid navigation task (混合导航) */
    public static final byte CMD_HYBRID_NAV_TASK = (byte) 0xAE;
    /** Query robot status */
    public static final byte CMD_QUERY_ROBOT_STATUS = (byte) 0xAF;
    /** Query cargo status */
    public static final byte CMD_QUERY_CARGO_STATUS = (byte) 0xB0;
    /** Send subscription signal */
    public static final byte CMD_SUBSCRIPTION = (byte) 0xB1;
    /** Immediate action command */
    public static final byte CMD_IMMEDIATE_ACTION = (byte) 0xB2;
    /** Set capability set */
    public static final byte CMD_SET_CAPABILITY = (byte) 0xB7;
    /** Send nearby vehicle info for obstacle avoidance */
    public static final byte CMD_NEARBY_VEHICLE_INFO = (byte) 0xB9;
    /** Query traffic management resource request */
    public static final byte CMD_QUERY_TRAFFIC_REQUEST = (byte) 0x70;
    /** Notify traffic management resource occupation result */
    public static final byte CMD_NOTIFY_TRAFFIC_RESULT = (byte) 0x71;
}
