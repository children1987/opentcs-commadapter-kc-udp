package com.kecong.opentcs.protocol.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * QR code navigation task model (0xF1 / 0xF5).
 * <p>
 * 0xF1: Simple task, max 30 segments, no response payload.<br>
 * 0xF5: Long-path task, up to 2048 total segments sent in batches of 1~30 via offset.
 * Response echoes global task metadata (U16 totalSegments, U8 angleLimitEnabled, U8 angleLimit, U8[8] reserved).
 */
public class QrNavigationTask {

    /** Max segments per 0xF1 / per 0xF5 batch */
    public static final int MAX_SEGMENTS_PER_BATCH = 30;

    private final int totalSegmentCount;
    private final int segmentOffset;   // 0 for 0xF1, >=0 for 0xF5
    private final boolean angleLimitEnabled;
    private final int angleLimit;      // 0~90 degrees
    private final List<Segment> segments;

    private QrNavigationTask(Builder builder) {
        this.totalSegmentCount = builder.totalSegmentCount;
        this.segmentOffset = builder.segmentOffset;
        this.angleLimitEnabled = builder.angleLimitEnabled;
        this.angleLimit = builder.angleLimit;
        this.segments = Collections.unmodifiableList(new ArrayList<>(builder.segments));
    }

    public int getTotalSegmentCount() { return totalSegmentCount; }
    public int getSegmentOffset() { return segmentOffset; }
    public boolean isAngleLimitEnabled() { return angleLimitEnabled; }
    public int getAngleLimit() { return angleLimit; }
    public List<Segment> getSegments() { return segments; }

    /**
     * A single QR navigation task segment (Landmark structure).
     * <pre>
     * 00H U16  destinationQrLabelId
     * 02H U16  routeSegmentId
     * 04H S16  dx (mm, +forward/-backward)
     * 06H S16  dy (mm, +forward/-backward)
     * 08H U16  destHeadingAngle (0.1°, 0~3599)
     * 0AH U8   stopFlag (0=no stop, 1=stop required)
     * 0BH U8   laserShieldEnable (0=disabled, 1=enabled)
     * 0CH U16  linearSpeed (cm/s)
     * 0EH U8   rotationLimit (0=bidirectional, 1=clockwise, 2=counter-clockwise)
     * 0FH U8   reserved
     * </pre>
     */
    public static class Segment {
        private final int destinationQrLabelId;
        private final int routeSegmentId;
        private final short dx;
        private final short dy;
        private final int destHeadingAngle;  // 0.1° units
        private final boolean stopRequired;
        private final boolean laserShieldEnabled;
        private final int linearSpeed;       // cm/s
        private final int rotationLimit;     // 0=bidirectional, 1=CW, 2=CCW

        private Segment(Builder builder) {
            this.destinationQrLabelId = builder.destinationQrLabelId;
            this.routeSegmentId = builder.routeSegmentId;
            this.dx = builder.dx;
            this.dy = builder.dy;
            this.destHeadingAngle = builder.destHeadingAngle;
            this.stopRequired = builder.stopRequired;
            this.laserShieldEnabled = builder.laserShieldEnabled;
            this.linearSpeed = builder.linearSpeed;
            this.rotationLimit = builder.rotationLimit;
        }

        public int getDestinationQrLabelId() { return destinationQrLabelId; }
        public int getRouteSegmentId() { return routeSegmentId; }
        public short getDx() { return dx; }
        public short getDy() { return dy; }
        public int getDestHeadingAngle() { return destHeadingAngle; }
        public boolean isStopRequired() { return stopRequired; }
        public boolean isLaserShieldEnabled() { return laserShieldEnabled; }
        public int getLinearSpeed() { return linearSpeed; }
        public int getRotationLimit() { return rotationLimit; }

        public static class Builder {
            private final int destinationQrLabelId;
            private final int routeSegmentId;
            private short dx;
            private short dy;
            private int destHeadingAngle;
            private boolean stopRequired;
            private boolean laserShieldEnabled;
            private int linearSpeed = 30;  // default 30 cm/s
            private int rotationLimit;

            public Builder(int destinationQrLabelId, int routeSegmentId) {
                this.destinationQrLabelId = destinationQrLabelId;
                this.routeSegmentId = routeSegmentId;
            }
            public Builder dx(short dx) { this.dx = dx; return this; }
            public Builder dy(short dy) { this.dy = dy; return this; }
            public Builder destHeadingAngle(int angle) { this.destHeadingAngle = angle; return this; }
            public Builder stopRequired(boolean v) { this.stopRequired = v; return this; }
            public Builder laserShieldEnabled(boolean v) { this.laserShieldEnabled = v; return this; }
            public Builder linearSpeed(int speed) { this.linearSpeed = speed; return this; }
            public Builder rotationLimit(int limit) { this.rotationLimit = limit; return this; }
            public Segment build() { return new Segment(this); }
        }
    }

    // ===== Builder =====

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int totalSegmentCount;
        private int segmentOffset;
        private boolean angleLimitEnabled;
        private int angleLimit;
        private final List<Segment> segments = new ArrayList<>();

        public Builder totalSegmentCount(int v) { this.totalSegmentCount = v; return this; }
        public Builder segmentOffset(int v) { this.segmentOffset = v; return this; }
        public Builder angleLimitEnabled(boolean v) { this.angleLimitEnabled = v; return this; }
        public Builder angleLimit(int v) { this.angleLimit = v; return this; }
        public Builder addSegment(Segment seg) {
            if (segments.size() >= MAX_SEGMENTS_PER_BATCH) {
                throw new IllegalStateException("Max " + MAX_SEGMENTS_PER_BATCH + " segments per batch");
            }
            segments.add(seg);
            return this;
        }
        public QrNavigationTask build() {
            if (totalSegmentCount == 0) totalSegmentCount = segments.size();
            return new QrNavigationTask(this);
        }
    }

    // ===== Encoder =====

    /**
     * Encode as 0xF1 simple QR navigation task request payload.
     * <pre>
     * 00H U16  segmentCount
     * 02H U8   angleLimitEnabled
     * 03H U8   angleLimit
     * 04H S16  reserved
     * 06H S16  reserved
     * 08H Segment[segmentCount] — each 16 bytes
     * </pre>
     */
    public byte[] toF1Bytes() {
        int segCount = segments.size();
        ByteBuffer buf = ByteBuffer.allocate(8 + segCount * 16).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) segCount);
        buf.put((byte) (angleLimitEnabled ? 1 : 0));
        buf.put((byte) angleLimit);
        buf.putShort((short) 0); // reserved
        buf.putShort((short) 0); // reserved
        encodeSegments(buf);
        return buf.array();
    }

    /**
     * Encode as 0xF5 long-path QR navigation task request payload.
     * <pre>
     * 00H U16  totalSegmentCount (1~2048)
     * 02H U8   angleLimitEnabled
     * 03H U8   angleLimit
     * 04H U8[8] reserved
     * 0CH U16  batchOffset
     * 0EH U16  batchSegmentCount
     * 10H Segment[batchSegmentCount]
     * </pre>
     */
    public byte[] toF5Bytes() {
        int batchCount = segments.size();
        ByteBuffer buf = ByteBuffer.allocate(16 + batchCount * 16).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) totalSegmentCount);
        buf.put((byte) (angleLimitEnabled ? 1 : 0));
        buf.put((byte) angleLimit);
        buf.put(new byte[8]); // reserved
        buf.putShort((short) segmentOffset);
        buf.putShort((short) batchCount);
        encodeSegments(buf);
        return buf.array();
    }

    private void encodeSegments(ByteBuffer buf) {
        for (Segment s : segments) {
            buf.putShort((short) s.destinationQrLabelId);
            buf.putShort((short) s.routeSegmentId);
            buf.putShort(s.dx);
            buf.putShort(s.dy);
            buf.putShort((short) s.destHeadingAngle);
            buf.put((byte) (s.stopRequired ? 1 : 0));
            buf.put((byte) (s.laserShieldEnabled ? 1 : 0));
            buf.putShort((short) s.linearSpeed);
            buf.put((byte) s.rotationLimit);
            buf.put((byte) 0); // reserved
        }
    }

    // ===== Decoder for 0xF5 response =====

    /**
     * Decode a 0xF5 long-path task response (echo).
     * <pre>
     * 00H U16  totalSegmentCount
     * 02H U8   angleLimitEnabled
     * 03H U8   angleLimit
     * 04H U8[8] reserved
     * </pre>
     */
    public static QrNavigationTask fromF5Response(byte[] data) {
        if (data == null || data.length < 12) return null;
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int totalCount = buf.getShort() & 0xFFFF;
        boolean angleEnabled = buf.get() != 0;
        int angleLimit = buf.get() & 0xFF;
        // skip reserved
        return builder()
                .totalSegmentCount(totalCount)
                .angleLimitEnabled(angleEnabled)
                .angleLimit(angleLimit)
                .build();
    }

    @Override
    public String toString() {
        return "QrNavigationTask{totalSegments=" + totalSegmentCount + ", offset=" + segmentOffset
                + ", angleLimit=" + (angleLimitEnabled ? angleLimit : "disabled")
                + ", batchSegments=" + segments.size() + '}';
    }
}
