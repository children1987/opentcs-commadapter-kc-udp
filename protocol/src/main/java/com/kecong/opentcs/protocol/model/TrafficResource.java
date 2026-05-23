package com.kecong.opentcs.protocol.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Traffic management resource request/result model (0x70/0x71).
 * <p>
 * Protocol: The dispatcher polls the robot's pending resource requests (0x70),
 * then responds with success/failure and which paths were granted (0x71).
 * Each request/response carries up to 16 path entries.
 */
public class TrafficResource {

    /** Max paths per request/result */
    public static final int MAX_PATHS = 16;

    /** Whether a request is pending (for 0x70 response) */
    private final boolean hasRequest;

    /** For 0x71: true = occupy succeeded, false = failed */
    private final boolean success;

    /** Path entries in this resource message */
    private final List<Path> paths;

    private TrafficResource(boolean hasRequest, boolean success, List<Path> paths) {
        this.hasRequest = hasRequest;
        this.success = success;
        this.paths = paths != null ? Collections.unmodifiableList(new ArrayList<>(paths)) : Collections.emptyList();
    }

    public boolean hasRequest() { return hasRequest; }
    public boolean isSuccess() { return success; }
    public List<Path> getPaths() { return paths; }
    public int getPathCount() { return paths.size(); }

    /**
     * A traffic resource path entry (path ID + endpoint ID).
     */
    public static class Path {
        private final int pathId;
        private final int endpointId;

        public Path(int pathId, int endpointId) {
            this.pathId = pathId;
            this.endpointId = endpointId;
        }

        public int getPathId() { return pathId; }
        public int getEndpointId() { return endpointId; }

        @Override
        public String toString() {
            return "Path{pathId=" + pathId + ", endpointId=" + endpointId + '}';
        }
    }

    // ===== Decoders =====

    /**
     * Decode a 0x70 query traffic request response (from robot).
     * <pre>
     * 00H U8  resourceRequestInstruction: 0=no request, 1=request occupy
     * 01H U8  pathCount (max 16)
     * 02H U8[2] reserved
     * 04H PATH[pathCount] — each PATH: U16 pathId, U16 endpointId
     * </pre>
     */
    public static TrafficResource fromQueryResponse(byte[] data) {
        if (data == null || data.length < 4) {
            return new TrafficResource(false, false, Collections.emptyList());
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        boolean hasRequest = (buf.get() & 0xFF) == 1;
        int pathCount = buf.get() & 0xFF;
        buf.getShort(); // reserved
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < Math.min(pathCount, MAX_PATHS); i++) {
            if (buf.remaining() < 4) break;
            paths.add(new Path(buf.getShort() & 0xFFFF, buf.getShort() & 0xFFFF));
        }
        return new TrafficResource(hasRequest, false, paths);
    }

    // ===== Builder / Encoder =====

    /**
     * Create a 0x71 notify result to send to the robot.
     */
    public static Builder newResult(boolean success) {
        return new Builder(success);
    }

    public static class Builder {
        private final boolean success;
        private final List<Path> paths = new ArrayList<>();

        Builder(boolean success) { this.success = success; }

        public Builder addPath(int pathId, int endpointId) {
            if (paths.size() >= MAX_PATHS) {
                throw new IllegalStateException("Max " + MAX_PATHS + " paths");
            }
            paths.add(new Path(pathId, endpointId));
            return this;
        }

        public TrafficResource build() {
            return new TrafficResource(false, success, paths);
        }
    }

    /**
     * Encode a 0x71 notify result request payload.
     * <pre>
     * 00H U8  result: 0=failed, 1=success
     * 01H U8  pathCount
     * 02H U8[2] reserved
     * 04H PATH[pathCount]
     * </pre>
     */
    public byte[] toNotifyBytes() {
        ByteBuffer buf = ByteBuffer.allocate(4 + MAX_PATHS * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) (success ? 1 : 0));
        buf.put((byte) paths.size());
        buf.putShort((short) 0); // reserved
        for (Path p : paths) {
            buf.putShort((short) p.pathId);
            buf.putShort((short) p.endpointId);
        }
        byte[] result = new byte[buf.position()];
        buf.flip();
        buf.get(result);
        return result;
    }

    @Override
    public String toString() {
        return "TrafficResource{hasRequest=" + hasRequest + ", success=" + success + ", paths=" + paths + '}';
    }
}
