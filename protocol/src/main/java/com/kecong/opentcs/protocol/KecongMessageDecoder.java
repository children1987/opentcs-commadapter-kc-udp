package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.MagneticNavTask;
import com.kecong.opentcs.protocol.model.QrNavigationTask;
import com.kecong.opentcs.protocol.model.RobotStatus;
import com.kecong.opentcs.protocol.model.TrafficResource;
import com.kecong.opentcs.protocol.model.RobotStatus.AbnormalEvent;
import com.kecong.opentcs.protocol.model.RobotStatus.ActionStatus;
import com.kecong.opentcs.util.ByteBufferUtils;

import java.nio.ByteBuffer;

/**
 * Decodes Kecong protocol response messages from byte arrays into model objects.
 */
public class KecongMessageDecoder {

    /**
     * Decode robot status from a 0xAF command response.
     */
    public static RobotStatus decodeRobotStatus(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        ByteBuffer buf = ByteBufferUtils.wrap(data);
        RobotStatus status = new RobotStatus();

        int abnormalSize = buf.get() & 0xFF;
        int actionSize = buf.get() & 0xFF;
        buf.getShort(); // reserved

        // Location status (LocationStatusInfo)
        status.setPositionX(buf.getFloat());
        status.setPositionY(buf.getFloat());
        status.setHeadingAngle(buf.getFloat());
        status.setLastPassedPointId(buf.getInt());
        status.setLastPassedPathId(buf.getInt());
        status.setPointSequenceNumber(buf.getInt());
        status.setConfidence(buf.get() & 0xFF);
        status.setLocalizationStatus(buf.get() & 0xFF);
        buf.position(buf.position() + 6); // reserved

        // Running status (RunningStatusInfo)
        status.setVelocityX(buf.getFloat());
        status.setVelocityY(buf.getFloat());
        status.setAngularVelocity(buf.getFloat());
        status.setWorkMode(buf.get() & 0xFF);
        status.setAgvState(buf.get() & 0xFF);
        status.setCapabilitySet(buf.get() & 0xFF);
        buf.position(buf.position() + 5); // reserved

        // Task status (TaskStatusInfo)
        status.setOrderId(buf.getInt());
        status.setTaskKey(buf.getInt());
        int pointSize = buf.get() & 0xFF;
        int pathSize = buf.get() & 0xFF;
        buf.position(buf.position() + 2); // reserved

        // Skip point/segment state sequences
        buf.position(buf.position() + pointSize * 8 + pathSize * 8);

        // Battery status
        status.setBatteryPercent(buf.getFloat());
        status.setBatteryVoltage(buf.getFloat());
        status.setBatteryCurrent(buf.getFloat());
        status.setChargeStatus(buf.get() & 0xFF);
        buf.position(buf.position() + 7); // reserved

        // Abnormal events
        if (abnormalSize > 0 && buf.remaining() >= abnormalSize * 12) {
            AbnormalEvent[] events = new AbnormalEvent[abnormalSize];
            for (int i = 0; i < abnormalSize; i++) {
                int eventCode = buf.getShort() & 0xFFFF;
                int level = buf.getShort() & 0xFFFF;
                buf.position(buf.position() + 8); // reserved
                events[i] = new AbnormalEvent(eventCode, level);
            }
            status.setAbnormalEvents(events);
        }

        // Action statuses
        if (actionSize > 0 && buf.remaining() >= actionSize * 12) {
            ActionStatus[] actions = new ActionStatus[actionSize];
            for (int i = 0; i < actionSize; i++) {
                int actionId = buf.getInt();
                int actionStat = buf.get() & 0xFF;
                buf.position(buf.position() + 7); // reserved
                actions[i] = new ActionStatus(actionId, actionStat);
            }
            status.setActionStatuses(actions);
        }

        return status;
    }

    /**
     * Decode cargo status from a 0xB0 command response.
     *
     * @param data response data payload
     * @return true if loaded, false if unloaded
     */
    /**
     * Decode a 0x70 query traffic request response from the robot.
     *
     * @param data response data payload
     * @return decoded traffic resource, or empty if no request
     */
    public static TrafficResource decodeTrafficRequest(byte[] data) {
        return TrafficResource.fromQueryResponse(data);
    }

    /**
     * Decode a 0xF5 long-path QR navigation task response (echo).
     *
     * @param data response data payload
     * @return decoded task metadata, or null if data invalid
     */
    public static QrNavigationTask decodeQrLongPathTaskResponse(byte[] data) {
        return QrNavigationTask.fromF5Response(data);
    }

    /**
     * Decode a 0xE2 magnetic navigation status response.
     *
     * @param data response data payload
     * @return decoded magnetic status, or null if data invalid
     */
    public static MagneticNavTask.MagneticStatus decodeMagneticStatus(byte[] data) {
        return MagneticNavTask.fromE2Response(data);
    }

    /**
     * Decode robot status from a 0x17 QUERY_RUN_STATUS response (per "调度" protocol).
     * Uses DOUBLE coordinates and different field offsets from the legacy 0xAF format.
     */
    public static RobotStatus decodeRunStatus(byte[] data) {
        if (data == null || data.length < 0xC0) {
            return null;
        }
        ByteBuffer buf = ByteBufferUtils.wrap(data);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        RobotStatus status = new RobotStatus();

        // 0x00 DOUBLE body temp (skip)
        buf.getDouble();
        // 0x08 DOUBLE pos_x (m)
        status.setPositionX((float) buf.getDouble());
        // 0x10 DOUBLE pos_y (m)
        status.setPositionY((float) buf.getDouble());
        // 0x18 DOUBLE heading (rad)
        status.setHeadingAngle((float) buf.getDouble());
        // 0x20 DOUBLE battery 0~1
        status.setBatteryPercent((float) buf.getDouble());
        // 0x28 U8 blocked, 0x29 U8 charging
        buf.get(); // blocked
        buf.get(); // charging
        // 0x2A U8 run_mode: 0=manual, 1=auto -> map to workMode
        int runMode = buf.get() & 0xFF;
        status.setWorkMode(runMode == 1 ? 3 : 1); // 3=AUTO, 1=MANUAL
        // 0x2B U8 map_loaded
        buf.get();
        // 0x2C U32 current target point id
        status.setOrderId(buf.getInt());
        // 0x30 DOUBLE velocity_x
        status.setVelocityX((float) buf.getDouble());
        // 0x38 DOUBLE angular_velocity
        status.setAngularVelocity((float) buf.getDouble());
        // 0x40 DOUBLE battery_voltage
        status.setBatteryVoltage((float) buf.getDouble());
        // 0x48 DOUBLE current
        status.setBatteryCurrent((float) buf.getDouble());
        // 0x50 U8 task_state: 0=none,1=wait,2=going,3=pause,4=done,5=fail
        int taskState = buf.get() & 0xFF;
        status.setNavTaskState(taskState);
        status.setAgvState(taskStateToAgvState(taskState));
        // 0x51-0x6F reserved + stats (skip to 0x70)
        buf.position(0x70);
        // 0x70 U8 loc_status: 0=fail,1=success,2=locating,3=done
        status.setLocalizationStatus(buf.get() & 0xFF);
        // 0x71-0x77 reserved (skip to 0xB8 via position)
        buf.position(0xB8);
        // 0xB8 FLOAT32 confidence 0~1
        float conf = buf.getFloat();
        status.setConfidence((int) (conf * 100));

        return status;
    }

    private static int taskStateToAgvState(int taskState) {
        switch (taskState) {
            case 1: return 0; // WAIT -> IDLE
            case 2: return 1; // GOING -> RUNNING
            case 3: return 2; // PAUSE -> PAUSED
            case 5: return 6; // FAIL -> NAV_FAILED
            default: return 0; // NONE/DONE -> IDLE
        }
    }

    public static boolean decodeCargoStatus(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        return data[0] == (byte) 0x01;
    }
}
