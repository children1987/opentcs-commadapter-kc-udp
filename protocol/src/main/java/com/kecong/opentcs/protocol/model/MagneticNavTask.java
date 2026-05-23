package com.kecong.opentcs.protocol.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Magnetic navigation task models covering 0xE0~0xE3 commands.
 *
 * <h3>Command mapping</h3>
 * <ul>
 *   <li>0xE0: Task download — {@link #toE0Bytes()}</li>
 *   <li>0xE1: Task control — {@link MagneticControl#toBytes()}</li>
 *   <li>0xE2: Status query — {@link #fromE2Response(byte[])}</li>
 *   <li>0xE3: Vehicle relocation — {@link MagneticRelocalize#toBytes()}</li>
 * </ul>
 */
public class MagneticNavTask {

    // Dispatcher-level magnetic task (0xE0)
    private final int totalSegmentCount;
    private final int totalTagmarkCount;
    private final int stopDistance;        // stop distance in cm
    private final List<Landmark> landmarks;
    private final List<Tagmark> tagmarks;

    private MagneticNavTask(Builder builder) {
        this.totalSegmentCount = builder.totalSegmentCount;
        this.totalTagmarkCount = builder.totalTagmarkCount;
        this.stopDistance = builder.stopDistance;
        this.landmarks = Collections.unmodifiableList(new ArrayList<>(builder.landmarks));
        this.tagmarks = Collections.unmodifiableList(new ArrayList<>(builder.tagmarks));
    }

    public int getTotalSegmentCount() { return totalSegmentCount; }
    public int getTotalTagmarkCount() { return totalTagmarkCount; }
    public int getStopDistance() { return stopDistance; }
    public List<Landmark> getLandmarks() { return landmarks; }
    public List<Tagmark> getTagmarks() { return tagmarks; }

    /**
     * Magnetic navigation segment (Landmark / 路段信息), 10 bytes.
     * <pre>
     * 00H U16  segmentId
     * 02H U8   motionControl: high nibble = segmentType (1=magnetic tape, 2=magnetic nail),
     *          low nibble = motionMode (1=forward, 2=backward)
     * 03H U8   trafficMgmt: 0=disabled, 1=enabled
     * 04H U16  segmentLength (cm)
     * 06H U16  straightSpeed (cm/s)
     * 08H U16  angularVelocity (0.01 rad/s)
     * 0AH U16  derailDistance (cm, 0=ignore)
     * </pre>
     */
    public static class Landmark {
        private final int segmentId;
        private final int segmentType;      // 1=magnetic tape, 2=magnetic nail
        private final int motionMode;       // 1=forward, 2=backward
        private final boolean trafficMgmtEnabled;
        private final int segmentLength;    // cm
        private final int straightSpeed;    // cm/s
        private final int angularVelocity;  // 0.01 rad/s
        private final int derailDistance;   // cm, 0=ignore

        private Landmark(Builder builder) {
            this.segmentId = builder.segmentId;
            this.segmentType = builder.segmentType;
            this.motionMode = builder.motionMode;
            this.trafficMgmtEnabled = builder.trafficMgmtEnabled;
            this.segmentLength = builder.segmentLength;
            this.straightSpeed = builder.straightSpeed;
            this.angularVelocity = builder.angularVelocity;
            this.derailDistance = builder.derailDistance;
        }

        public int getSegmentId() { return segmentId; }
        public int getSegmentType() { return segmentType; }
        public int getMotionMode() { return motionMode; }
        public boolean isTrafficMgmtEnabled() { return trafficMgmtEnabled; }
        public int getSegmentLength() { return segmentLength; }
        public int getStraightSpeed() { return straightSpeed; }
        public int getAngularVelocity() { return angularVelocity; }
        public int getDerailDistance() { return derailDistance; }

        /** Encode to 10 bytes */
        void encodeTo(ByteBuffer buf) {
            buf.putShort((short) segmentId);
            int motionControl = (segmentType << 4) | (motionMode & 0x0F);
            buf.put((byte) motionControl);
            buf.put((byte) (trafficMgmtEnabled ? 1 : 0));
            buf.putShort((short) segmentLength);
            buf.putShort((short) straightSpeed);
            buf.putShort((short) angularVelocity);
            buf.putShort((short) derailDistance);
        }

        public static Builder builder(int segmentId) { return new Builder(segmentId); }

        public static class Builder {
            private final int segmentId;
            private int segmentType = 1;     // default: magnetic tape
            private int motionMode = 1;      // default: forward
            private boolean trafficMgmtEnabled;
            private int segmentLength = 100; // default 100 cm
            private int straightSpeed = 30;  // default 30 cm/s
            private int angularVelocity;
            private int derailDistance;
            Builder(int segmentId) { this.segmentId = segmentId; }
            public Builder segmentType(int v) { this.segmentType = v; return this; }
            public Builder motionMode(int v) { this.motionMode = v; return this; }
            public Builder trafficMgmtEnabled(boolean v) { this.trafficMgmtEnabled = v; return this; }
            public Builder segmentLength(int v) { this.segmentLength = v; return this; }
            public Builder straightSpeed(int v) { this.straightSpeed = v; return this; }
            public Builder angularVelocity(int v) { this.angularVelocity = v; return this; }
            public Builder derailDistance(int v) { this.derailDistance = v; return this; }
            public Landmark build() { return new Landmark(this); }
        }
    }

    /**
     * Magnetic navigation tagmark (路标信息), 11 bytes.
     * <pre>
     * 00H U16  owningSegmentId
     * 02H U8   reserved
     * 03H U8[4] landmarkId / pathPointId
     * 07H U16  reserved
     * </pre>
     */
    public static class Tagmark {
        private final int owningSegmentId;
        private final int landmarkId;

        public Tagmark(int owningSegmentId, int landmarkId) {
            this.owningSegmentId = owningSegmentId;
            this.landmarkId = landmarkId;
        }

        public int getOwningSegmentId() { return owningSegmentId; }
        public int getLandmarkId() { return landmarkId; }

        void encodeTo(ByteBuffer buf) {
            buf.putShort((short) owningSegmentId);
            buf.put((byte) 0); // reserved
            buf.put((byte) 0); // padding
            buf.putInt(landmarkId);
        }
    }

    // ===== Builder for 0xE0 =====

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int totalSegmentCount;
        private int totalTagmarkCount;
        private int stopDistance;
        private final List<Landmark> landmarks = new ArrayList<>();
        private final List<Tagmark> tagmarks = new ArrayList<>();

        public Builder totalSegmentCount(int v) { this.totalSegmentCount = v; return this; }
        public Builder totalTagmarkCount(int v) { this.totalTagmarkCount = v; return this; }
        public Builder stopDistance(int v) { this.stopDistance = v; return this; }
        public Builder addLandmark(Landmark lm) { landmarks.add(lm); return this; }
        public Builder addTagmark(Tagmark tm) { tagmarks.add(tm); return this; }
        public MagneticNavTask build() {
            if (totalSegmentCount == 0) totalSegmentCount = landmarks.size();
            if (totalTagmarkCount == 0) totalTagmarkCount = tagmarks.size();
            return new MagneticNavTask(this);
        }
    }

    // ===== Encoder: 0xE0 task download =====
    /**
     * Encode as 0xE0 magnetic navigation task dispatch.
     * <pre>
     * 00H U16   totalSegmentCount
     * 02H U16   totalTagmarkCount
     * 04H FLOAT stopDistance (cm)
     * 08H U8[4] reserved
     * 0CH U8[4] reserved
     * 10H Landmark[totalSegmentCount] — each 12 bytes
     *     Tagmark[totalTagmarkCount]   — each 8 bytes
     * </pre>
     */
    public byte[] toE0Bytes() {
        int header = 16;
        int lmSize = landmarks.size() * 12;
        int tmSize = tagmarks.size() * 8;
        ByteBuffer buf = ByteBuffer.allocate(header + lmSize + tmSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) totalSegmentCount);
        buf.putShort((short) totalTagmarkCount);
        buf.putFloat(stopDistance);
        buf.put(new byte[4]); // reserved
        buf.put(new byte[4]); // reserved
        for (Landmark lm : landmarks) { lm.encodeTo(buf); }
        for (Tagmark tm : tagmarks) { tm.encodeTo(buf); }
        return buf.array();
    }

    // ===== Decoder: 0xE2 status response =====
    /**
     * Decode a 0xE2 magnetic navigation status response (20 bytes).
     * <pre>
     * 00H U8    runStatus: 0=normal,1=landmark fault,2=derail,5=arc turn,6=right-angle,0xFF=unknown
     * 01H U8    reserved
     * 02H U8    navStatus: 0=idle,1=paused,2=executing,3=cancelled,4=completed
     * 03H U8    landmarkDetected: 0=no, 1=yes
     * 04H U16   currentSegmentId
     * 06H U8    navAllowed: 0=normal,1=not allowed
     * 07H U8    trafficControlWait: 0=no, 1=waiting
     * 08H FLOAT positionFromStart (cm)
     * 0CH S16   headingAngle (-180~180)
     * 0EH U8    headingValid: 0=invalid, 1=valid
     * 0FH U8    positioningState: 0=success,1=need reloc,2=locating,3=done
     * 10H U16   waitingSegmentId
     * 12H U16   pathStartPointId
     * </pre>
     */
    public static MagneticStatus fromE2Response(byte[] data) {
        if (data == null || data.length < 20) return null;
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        MagneticStatus status = new MagneticStatus();
        status.runStatus = buf.get() & 0xFF;
        buf.get(); // reserved
        status.navStatus = buf.get() & 0xFF;
        status.landmarkDetected = (buf.get() & 0xFF) == 1;
        status.currentSegmentId = buf.getShort() & 0xFFFF;
        buf.get(); // navAllowed
        status.trafficControlWait = (buf.get() & 0xFF) == 1;
        status.positionX = buf.getFloat();
        status.headingAngle = buf.getShort();
        buf.get(); // headingValid
        status.positioningState = buf.get() & 0xFF;
        status.waitingSegmentId = buf.getShort() & 0xFFFF;
        status.pathStartPointId = buf.getShort() & 0xFFFF;
        return status;
    }

    /**
     * Magnetic navigation vehicle status (decoded from 0xE2 response).
     */
    public static class MagneticStatus {
        private int runStatus;           // 0=normal,1=landmark fault,2=derail,5=arc turn,6=right-angle,0xFF=unknown
        private int navStatus;           // 0=idle,1=paused,2=executing,3=cancelled,4=completed
        private boolean landmarkDetected;
        private int currentSegmentId;
        private float positionX;         // cm (distance from segment start)
        private short headingAngle;      // -180~180 degrees
        private int positioningState;    // 0=success,1=need reloc,2=locating,3=done
        private boolean trafficControlWait;
        private int waitingSegmentId;
        private int pathStartPointId;

        // Getters
        public int getRunStatus() { return runStatus; }
        public int getNavStatus() { return navStatus; }
        public boolean isLandmarkDetected() { return landmarkDetected; }
        public int getCurrentSegmentId() { return currentSegmentId; }
        public float getPositionX() { return positionX; }
        public short getHeadingAngle() { return headingAngle; }
        public int getPositioningState() { return positioningState; }
        public boolean isTrafficControlWait() { return trafficControlWait; }
        public int getWaitingSegmentId() { return waitingSegmentId; }
        public int getPathStartPointId() { return pathStartPointId; }

        // Convenience predicates
        public boolean isRunning() { return navStatus == 2; }
        public boolean isPaused() { return navStatus == 1; }
        public boolean isFault() { return runStatus != 0; }
        public boolean isArrived() { return navStatus == 4; }
        public boolean isDerailed() { return runStatus == 2; }
        public boolean isLocated() { return positioningState == 0 || positioningState == 3; }

        @Override
        public String toString() {
            return "MagneticStatus{run=" + runStatus + ", nav=" + navStatus + ", landmark=" + landmarkDetected
                    + ", segId=" + currentSegmentId + ", pos=" + positionX
                    + ", heading=" + headingAngle + ", positioning=" + positioningState + '}';
        }
    }

    // ===== MagneticControl (0xE1) =====

    /**
     * Magnetic navigation task control command (0xE1).
     * <pre>
     * 00H U8  controlType: 0=pause, 1=resume, 2=cancel, 3=start, 6=clear fault
     * 01H U8  trafficMgmt: 0=disabled, 1=enabled
     * 02H U8[2] reserved
     * </pre>
     */
    public static class MagneticControl {
        /** Control type constants */
        public static final int CTRL_PAUSE = 0;
        public static final int CTRL_RESUME = 1;
        public static final int CTRL_CANCEL = 2;
        public static final int CTRL_START = 3;
        public static final int CTRL_CLEAR_FAULT = 6;

        private final int controlType;
        private final boolean trafficMgmtEnabled;

        public MagneticControl(int controlType, boolean trafficMgmtEnabled) {
            this.controlType = controlType;
            this.trafficMgmtEnabled = trafficMgmtEnabled;
        }

        public int getControlType() { return controlType; }
        public boolean isTrafficMgmtEnabled() { return trafficMgmtEnabled; }

        public byte[] toBytes() {
            ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            buf.put((byte) controlType);
            buf.put((byte) (trafficMgmtEnabled ? 1 : 0));
            buf.put(new byte[2]); // reserved
            return buf.array();
        }

        @Override
        public String toString() {
            return "MagneticControl{type=" + controlType + ", trafficMgmt=" + trafficMgmtEnabled + '}';
        }
    }

    // ===== MagneticRelocalize (0xE3) =====

    /**
     * Magnetic navigation vehicle relocation command (0xE3).
     * <pre>
     * 00H U16  segmentId
     * 02H S16  headingAngle (-180~180, degrees)
     * 04H U16  distanceFromStart (cm)
     * 06H U16  startPointId
     * 08H U8   segmentType: 0=magnetic tape, 1=magnetic nail
     * 09H U8[3] reserved
     * </pre>
     */
    public static class MagneticRelocalize {
        public static final int SEG_TYPE_MAGNETIC_TAPE = 0;
        public static final int SEG_TYPE_MAGNETIC_NAIL = 1;

        private final int segmentId;
        private final short headingAngle;  // -180~180 degrees
        private final float distanceFromStart; // cm
        private final int startPointId;
        private final int segmentType;      // 0=magnetic tape, 1=magnetic nail

        public MagneticRelocalize(int segmentId, short headingAngle, float distanceFromStart,
                                  int startPointId, int segmentType) {
            this.segmentId = segmentId;
            this.headingAngle = headingAngle;
            this.distanceFromStart = distanceFromStart;
            this.startPointId = startPointId;
            this.segmentType = segmentType;
        }

        public int getSegmentId() { return segmentId; }
        public short getHeadingAngle() { return headingAngle; }
        public float getDistanceFromStart() { return distanceFromStart; }
        public int getStartPointId() { return startPointId; }
        public int getSegmentType() { return segmentType; }

        public byte[] toBytes() {
            ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            buf.putShort((short) segmentId);
            buf.putShort(headingAngle);
            buf.putFloat(distanceFromStart);
            buf.putShort((short) startPointId);
            buf.put((byte) segmentType);
            buf.put((byte) 0); // reserved
            return buf.array();
        }

        @Override
        public String toString() {
            return "MagneticRelocalize{segId=" + segmentId + ", heading=" + headingAngle
                    + ", dist=" + distanceFromStart + ", startPt=" + startPointId
                    + ", type=" + (segmentType == 0 ? "tape" : "nail") + '}';
        }
    }

    @Override
    public String toString() {
        return "MagneticNavTask{segments=" + totalSegmentCount + ", tagmarks=" + totalTagmarkCount
                + ", stopDist=" + stopDistance + ", landmarks=" + landmarks.size() + '}';
    }
}
