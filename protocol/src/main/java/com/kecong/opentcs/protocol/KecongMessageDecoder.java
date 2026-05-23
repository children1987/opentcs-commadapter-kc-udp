package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.RobotStatus;
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
    public static boolean decodeCargoStatus(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        return data[0] == (byte) 0x01;
    }
}
