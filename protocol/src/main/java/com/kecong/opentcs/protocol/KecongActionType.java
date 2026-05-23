package com.kecong.opentcs.protocol;

/**
 * Kecong protocol action type codes (动作类型).
 * Based on "科聪控制器UDP接口协议说明书V2.0" Appendix 7.1.
 */
public final class KecongActionType {

    private KecongActionType() {}

    /** Pause */
    public static final short ACTION_PAUSE = (short) 0x01;
    /** Resume */
    public static final short ACTION_RESUME = (short) 0x02;
    /** Cancel task */
    public static final short ACTION_CANCEL = (short) 0x03;
    /** Fork lift up/down (叉齿升降) */
    public static final short ACTION_FORK_LIFT = (short) 0x12;
    /** Pallet lift up/down (托盘升降) */
    public static final short ACTION_PALLET_LIFT = (short) 0x16;
    /** Turntable follow */
    public static final short ACTION_TURNTABLE_FOLLOW = (short) 0x0001;
    /** Turntable position mode */
    public static final short ACTION_TURNTABLE_POSITION = (short) 0x0002;
    /** Turntable speed mode */
    public static final short ACTION_TURNTABLE_SPEED = (short) 0x0003;

    // ===== Action concurrency modes =====
    /** Move and action can be parallel */
    public static final byte CONCURRENT_ALL = (byte) 0x00;
    /** Actions can be parallel, but cannot move */
    public static final byte CONCURRENT_ACTION_ONLY = (byte) 0x01;
    /** Can only execute current action */
    public static final byte CONCURRENT_SINGLE = (byte) 0x02;
}
