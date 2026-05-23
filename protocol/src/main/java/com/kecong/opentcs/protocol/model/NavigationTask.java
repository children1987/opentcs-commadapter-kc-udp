package com.kecong.opentcs.protocol.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hybrid navigation task model for the 0xAE (下发混合导航任务) command.
 * Based on "科聪控制器UDP接口协议说明书V2.0" Section 5.5.1.
 */
public class NavigationTask {

    /** Navigation mode: path splicing (路径拼接) */
    public static final byte NAV_MODE_PATH_SPLICE = 0;
    /** Navigation mode: free navigation (自由导航) */
    public static final byte NAV_MODE_FREE = 1;
    /** Navigation mode: target point (目标点导航) */
    public static final byte NAV_MODE_TARGET_POINT = 2;

    private int orderId;
    private int taskKey;
    private byte navigationMode;
    private final List<TaskPoint> points;
    private final List<TaskPath> paths;

    private NavigationTask(Builder builder) {
        this.orderId = builder.orderId;
        this.taskKey = builder.taskKey;
        this.navigationMode = builder.navigationMode;
        this.points = Collections.unmodifiableList(new ArrayList<>(builder.points));
        this.paths = Collections.unmodifiableList(new ArrayList<>(builder.paths));
    }

    public int getOrderId() { return orderId; }
    public int getTaskKey() { return taskKey; }
    public byte getNavigationMode() { return navigationMode; }
    public List<TaskPoint> getPoints() { return points; }
    public List<TaskPath> getPaths() { return paths; }

    /**
     * A navigation point in the task.
     */
    public static class TaskPoint {
        private int sequenceNumber;      // even numbers: 0,2,4,6...
        private int pointId;             // map path point ID (for path splice mode)
        private Float angle;             // rad, optional
        private boolean specifyAngle;
        private final List<TaskAction> actions;

        // For free navigation mode
        private boolean useFreeCoordinates;
        private Float freeX;
        private Float freeY;

        private TaskPoint(Builder builder) {
            this.sequenceNumber = builder.sequenceNumber;
            this.pointId = builder.pointId;
            this.angle = builder.angle;
            this.specifyAngle = builder.specifyAngle;
            this.actions = Collections.unmodifiableList(new ArrayList<>(builder.actions));
        }

        public int getSequenceNumber() { return sequenceNumber; }
        public int getPointId() { return pointId; }
        public Float getAngle() { return angle; }
        public boolean isSpecifyAngle() { return specifyAngle; }
        public List<TaskAction> getActions() { return actions; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int sequenceNumber;
            private int pointId;
            private Float angle;
            private boolean specifyAngle;
            private List<TaskAction> actions = new ArrayList<>();

            public Builder sequenceNumber(int sn) { this.sequenceNumber = sn; return this; }
            public Builder pointId(int id) { this.pointId = id; return this; }
            public Builder angle(Float angle) { this.angle = angle; return this; }
            public Builder specifyAngle(boolean sa) { this.specifyAngle = sa; return this; }
            public Builder addAction(TaskAction action) { this.actions.add(action); return this; }
            public Builder actions(List<TaskAction> actions) { this.actions = actions; return this; }
            public TaskPoint build() { return new TaskPoint(this); }
        }
    }

    /**
     * A path segment in the task.
     */
    public static class TaskPath {
        private int sequenceNumber;      // odd numbers: 1,3,5,7...
        private int pathId;
        private Float fixedAngle;        // rad
        private boolean fixedAngleEnabled;
        private int travelPose;          // 0=forward,1=reverse,2=left,3=right
        private Float maxSpeed;          // m/s, null = use map default
        private Float maxAngularSpeed;   // rad/s, null = use map default
        private final List<TaskAction> actions;

        private TaskPath(Builder builder) {
            this.sequenceNumber = builder.sequenceNumber;
            this.pathId = builder.pathId;
            this.fixedAngle = builder.fixedAngle;
            this.fixedAngleEnabled = builder.fixedAngleEnabled;
            this.travelPose = builder.travelPose;
            this.maxSpeed = builder.maxSpeed;
            this.maxAngularSpeed = builder.maxAngularSpeed;
            this.actions = Collections.unmodifiableList(new ArrayList<>(builder.actions));
        }

        public int getSequenceNumber() { return sequenceNumber; }
        public int getPathId() { return pathId; }
        public Float getFixedAngle() { return fixedAngle; }
        public boolean isFixedAngleEnabled() { return fixedAngleEnabled; }
        public int getTravelPose() { return travelPose; }
        public Float getMaxSpeed() { return maxSpeed; }
        public Float getMaxAngularSpeed() { return maxAngularSpeed; }
        public List<TaskAction> getActions() { return actions; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int sequenceNumber;
            private int pathId;
            private Float fixedAngle;
            private boolean fixedAngleEnabled;
            private int travelPose;
            private Float maxSpeed;
            private Float maxAngularSpeed;
            private List<TaskAction> actions = new ArrayList<>();

            public Builder sequenceNumber(int sn) { this.sequenceNumber = sn; return this; }
            public Builder pathId(int id) { this.pathId = id; return this; }
            public Builder fixedAngle(Float angle) { this.fixedAngle = angle; return this; }
            public Builder fixedAngleEnabled(boolean enabled) { this.fixedAngleEnabled = enabled; return this; }
            public Builder travelPose(int pose) { this.travelPose = pose; return this; }
            public Builder maxSpeed(Float speed) { this.maxSpeed = speed; return this; }
            public Builder maxAngularSpeed(Float speed) { this.maxAngularSpeed = speed; return this; }
            public Builder addAction(TaskAction action) { this.actions.add(action); return this; }
            public Builder actions(List<TaskAction> actions) { this.actions = actions; return this; }
            public TaskPath build() { return new TaskPath(this); }
        }
    }

    /**
     * An action attached to a point or path segment.
     */
    public static class TaskAction {
        private short actionType;
        private byte concurrencyMode;   // 0=all, 1=action_only, 2=single
        private int actionId;
        private byte[] params;

        public TaskAction(short actionType, byte concurrencyMode, int actionId, byte[] params) {
            this.actionType = actionType;
            this.concurrencyMode = concurrencyMode;
            this.actionId = actionId;
            this.params = params != null ? params.clone() : new byte[0];
        }

        public short getActionType() { return actionType; }
        public byte getConcurrencyMode() { return concurrencyMode; }
        public int getActionId() { return actionId; }
        public byte[] getParams() { return params.clone(); }
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int orderId = 1;
        private int taskKey = 1;
        private byte navigationMode = NAV_MODE_PATH_SPLICE;
        private List<TaskPoint> points = new ArrayList<>();
        private List<TaskPath> paths = new ArrayList<>();

        public Builder orderId(int id) { this.orderId = id; return this; }
        public Builder taskKey(int key) { this.taskKey = key; return this; }
        public Builder navigationMode(byte mode) { this.navigationMode = mode; return this; }
        public Builder addPoint(TaskPoint point) { this.points.add(point); return this; }
        public Builder addPath(TaskPath path) { this.paths.add(path); return this; }
        public Builder points(List<TaskPoint> pts) { this.points = pts; return this; }
        public Builder paths(List<TaskPath> ps) { this.paths = ps; return this; }
        public NavigationTask build() { return new NavigationTask(this); }
    }
}
