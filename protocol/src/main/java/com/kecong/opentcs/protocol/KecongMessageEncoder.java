package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.MagneticNavTask;
import com.kecong.opentcs.protocol.model.MagneticNavTask.MagneticControl;
import com.kecong.opentcs.protocol.model.MagneticNavTask.MagneticRelocalize;
import com.kecong.opentcs.protocol.model.NavigationTask;
import com.kecong.opentcs.protocol.model.QrNavigationTask;
import com.kecong.opentcs.protocol.model.TrafficResource;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskAction;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPath;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPoint;
import com.kecong.opentcs.util.ByteBufferUtils;

import java.nio.ByteBuffer;

/**
 * Encodes Kecong protocol messages from model objects into byte arrays.
 */
public class KecongMessageEncoder {

    /**
     * Encode a subscription request (0xB1).
     *
     * @param commandCodes  command codes to subscribe to (e.g., 0xAF, 0xB0)
     * @param intervalMs    report interval in ms (min 50)
     * @param durationMs    report duration in ms
     * @param changeReport  whether to report only on change
     * @param uuid          unique subscription identifier
     * @return encoded byte array
     */
    public static byte[] encodeSubscription(byte[] commandCodes, int intervalMs, int durationMs, boolean changeReport, String uuid) {
        if (commandCodes == null || commandCodes.length > 8) {
            throw new IllegalArgumentException("commandCodes must be 1-8 items");
        }
        ByteBuffer buf = ByteBufferUtils.allocate(0x80 + 64);

        // Info[8] array
        for (int i = 0; i < 8; i++) {
            if (i < commandCodes.length) {
                buf.putShort((short) (commandCodes[i] & 0xFFFF));
            } else {
                buf.putShort((short) 0);  // disabled
            }
            buf.putShort((short) intervalMs);
            buf.putInt(durationMs);
            buf.put((byte) (changeReport ? 1 : 0));
            buf.put(new byte[7]); // reserved
        }

        // uuid (64 bytes, ASCII)
        ByteBufferUtils.putFixedString(buf, uuid, 64);

        return buf.array();
    }

    /**
     * Encode a hybrid navigation task (0xAE).
     */
    public static byte[] encodeNavigationTask(NavigationTask task) {
        // Calculate sizes
        int pointSize = task.getPoints().size();
        int pathSize = task.getPaths().size();

        // Point section: calculate size per point
        // Path splice: 0x14 + actions * 0x0C + aligned(actions)
        // We'll build dynamically
        ByteBuffer buf = ByteBufferUtils.allocate(KecongProtocolFrame.MAX_DATA_SIZE);

        // Header
        buf.putInt(task.getOrderId());
        buf.putInt(task.getTaskKey());
        buf.put((byte) pointSize);
        buf.put((byte) pathSize);
        buf.put(task.getNavigationMode());
        buf.put((byte) 0); // reserved

        // Encode points
        for (TaskPoint point : task.getPoints()) {
            encodePoint(buf, point, task.getNavigationMode());
        }

        // Encode paths
        for (TaskPath path : task.getPaths()) {
            encodePath(buf, path);
        }

        // Trim to actual size
        byte[] result = new byte[buf.position()];
        buf.flip();
        buf.get(result);
        return result;
    }

    private static void encodePoint(ByteBuffer buf, TaskPoint point, byte navMode) {
        buf.putInt(point.getSequenceNumber());

        if (navMode == NavigationTask.NAV_MODE_PATH_SPLICE) {
            buf.putInt(point.getPointId());
            buf.putFloat(point.getAngle() != null ? point.getAngle() : 0f);
            buf.put((byte) (point.isSpecifyAngle() ? 1 : 0));
            buf.put((byte) point.getActions().size());
            buf.put(new byte[6]); // reserved

            // Actions
            for (TaskAction action : point.getActions()) {
                encodeAction(buf, action);
            }
        } else {
            // Free navigation mode
            buf.putFloat(point.getAngle() != null ? point.getAngle() : 0f);
            buf.put((byte) (point.isSpecifyAngle() ? 1 : 0));
            buf.put((byte) point.getActions().size());
            buf.put((byte) 0); // DYNAMIC_POINT type: 0=point ID
            buf.put((byte) 0); // reserved
            buf.putInt(point.getPointId());
            buf.put(new byte[4]); // reserved

            for (TaskAction action : point.getActions()) {
                encodeAction(buf, action);
            }
        }
    }

    private static void encodePath(ByteBuffer buf, TaskPath path) {
        buf.putInt(path.getSequenceNumber());
        buf.putInt(path.getPathId());
        buf.putFloat(path.getFixedAngle() != null ? path.getFixedAngle() : 0f);
        buf.put((byte) (path.isFixedAngleEnabled() ? 1 : 0));
        buf.put((byte) path.getTravelPose());
        buf.put((byte) path.getActions().size());
        buf.put((byte) 0); // reserved
        buf.putFloat(path.getMaxSpeed() != null ? path.getMaxSpeed() : 0f);
        buf.putFloat(path.getMaxAngularSpeed() != null ? path.getMaxAngularSpeed() : 0f);
        buf.put(new byte[4]); // reserved

        for (TaskAction action : path.getActions()) {
            encodeAction(buf, action);
        }
    }

    private static void encodeAction(ByteBuffer buf, TaskAction action) {
        buf.putShort(action.getActionType());
        buf.put(action.getConcurrencyMode());
        buf.put((byte) 0); // reserved
        buf.putInt(action.getActionId());
        buf.put((byte) action.getParams().length);
        buf.put(new byte[3]); // reserved
        buf.put(action.getParams());
        // 4-byte alignment
        int pos = buf.position();
        int remainder = pos % 4;
        if (remainder != 0) {
            buf.put(new byte[4 - remainder]);
        }
    }

    /**
     * Encode an immediate action command (0xB2).
     *
     * @param actionType      action type (e.g., ACTION_PAUSE, ACTION_FORK_LIFT)
     * @param concurrencyMode concurrency mode
     * @param actionId        unique action ID
     * @param params          action-specific parameters
     * @return encoded byte array
     */
    public static byte[] encodeImmediateAction(short actionType, byte concurrencyMode, int actionId, byte[] params) {
        int paramLen = params != null ? params.length : 0;
        ByteBuffer buf = ByteBufferUtils.allocate(0x0C + paramLen + 4);

        buf.putShort(actionType);
        buf.put(concurrencyMode);
        buf.put((byte) 0); // reserved
        buf.putInt(actionId);
        buf.put((byte) paramLen);
        buf.put(new byte[3]); // reserved
        if (params != null && params.length > 0) {
            buf.put(params);
        }
        // 4-byte alignment
        ByteBufferUtils.alignTo4(buf);

        byte[] result = new byte[buf.position()];
        buf.flip();
        buf.get(result);
        return result;
    }

    /**
     * Encode a 0x71 traffic management notify result.
     *
     * @param resource the traffic resource result to send to the robot
     * @return encoded byte array
     */
    public static byte[] encodeTrafficResult(TrafficResource resource) {
        if (resource == null) {
            return new byte[0];
        }
        return resource.toNotifyBytes();
    }

    /**
     * Encode a 0xF1 simple QR code navigation task.
     */
    public static byte[] encodeQrNavTask(QrNavigationTask task) {
        if (task == null) {
            return new byte[0];
        }
        return task.toF1Bytes();
    }

    /**
     * Encode a 0xF5 long-path QR code navigation task.
     */
    public static byte[] encodeQrLongPathTask(QrNavigationTask task) {
        if (task == null) {
            return new byte[0];
        }
        return task.toF5Bytes();
    }

    /**
     * Encode a 0xE0 magnetic navigation task download.
     */
    public static byte[] encodeMagneticTask(MagneticNavTask task) {
        if (task == null) {
            return new byte[0];
        }
        return task.toE0Bytes();
    }

    /**
     * Encode a 0xE1 magnetic navigation task control command.
     */
    public static byte[] encodeMagneticControl(MagneticControl control) {
        if (control == null) {
            return new byte[0];
        }
        return control.toBytes();
    }

    /**
     * Encode a 0xE3 magnetic navigation vehicle relocation command.
     */
    public static byte[] encodeMagneticRelocalize(MagneticRelocalize relocalize) {
        if (relocalize == null) {
            return new byte[0];
        }
        return relocalize.toBytes();
    }

    /**
     * Encode a simple request with no data payload (e.g., query robot status 0xAF).
     */
    public static byte[] encodeEmptyRequest() {
        return new byte[0];
    }
}
