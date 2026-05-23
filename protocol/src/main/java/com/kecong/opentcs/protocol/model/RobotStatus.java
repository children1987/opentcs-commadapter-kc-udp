package com.kecong.opentcs.protocol.model;

/**
 * Robot status information decoded from the 0xAF (查询机器人状态) command response.
 * Based on "科聪控制器UDP接口协议说明书V2.0" Section 5.5.2.
 */
public class RobotStatus {

    // Location
    private float positionX;        // m
    private float positionY;        // m
    private float headingAngle;     // rad
    private int lastPassedPointId;
    private int lastPassedPathId;
    private int pointSequenceNumber;
    private int confidence;         // 0-100
    private int localizationStatus; // 0=fail, 1=success, 2=locating, 3=done

    // Running
    private float velocityX;        // m/s
    private float velocityY;        // m/s
    private float angularVelocity;  // rad/s
    private int workMode;           // 0=standby,1=manual,2=semi-auto,3=auto,4=teach,5=service,6=repair
    private int agvState;           // 0=idle,1=running,2=paused,3=uninitialized,4=manual_confirm,6=nav_failed
    private int capabilitySet;      // 0=not set, 1=set

    // Task
    private int orderId;            // 0 = no task
    private int taskKey;

    // Battery
    private float batteryPercent;   // 0.0~1.0
    private float batteryVoltage;   // V
    private float batteryCurrent;   // A
    private int chargeStatus;       // 0=discharging,1=charging,2=full

    // Abnormal events
    private AbnormalEvent[] abnormalEvents;

    // Actions
    private ActionStatus[] actionStatuses;

    // Nested types
    public static class AbnormalEvent {
        private final int eventCode;
        private final int level;    // 0=info, 1=warning, 2=error

        public AbnormalEvent(int eventCode, int level) {
            this.eventCode = eventCode;
            this.level = level;
        }

        public int getEventCode() { return eventCode; }
        public int getLevel() { return level; }

        public boolean isError() { return level == 2; }
        public boolean isWarning() { return level == 1; }
    }

    public static class ActionStatus {
        private final int actionId;
        private final int status;   // 0=waiting,1=initializing,2=executing,3=complete,4=failed,5=cancelled,6=paused

        public ActionStatus(int actionId, int status) {
            this.actionId = actionId;
            this.status = status;
        }

        public int getActionId() { return actionId; }
        public int getStatus() { return status; }
        public boolean isComplete() { return status == 3; }
        public boolean isFailed() { return status == 4; }
        public boolean isExecuting() { return status == 2; }
    }

    // Getters and setters
    public float getPositionX() { return positionX; }
    public void setPositionX(float positionX) { this.positionX = positionX; }

    public float getPositionY() { return positionY; }
    public void setPositionY(float positionY) { this.positionY = positionY; }

    public float getHeadingAngle() { return headingAngle; }
    public void setHeadingAngle(float headingAngle) { this.headingAngle = headingAngle; }

    public int getLastPassedPointId() { return lastPassedPointId; }
    public void setLastPassedPointId(int lastPassedPointId) { this.lastPassedPointId = lastPassedPointId; }

    public int getLastPassedPathId() { return lastPassedPathId; }
    public void setLastPassedPathId(int lastPassedPathId) { this.lastPassedPathId = lastPassedPathId; }

    public int getPointSequenceNumber() { return pointSequenceNumber; }
    public void setPointSequenceNumber(int pointSequenceNumber) { this.pointSequenceNumber = pointSequenceNumber; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public int getLocalizationStatus() { return localizationStatus; }
    public void setLocalizationStatus(int localizationStatus) { this.localizationStatus = localizationStatus; }

    public float getVelocityX() { return velocityX; }
    public void setVelocityX(float velocityX) { this.velocityX = velocityX; }

    public float getVelocityY() { return velocityY; }
    public void setVelocityY(float velocityY) { this.velocityY = velocityY; }

    public float getAngularVelocity() { return angularVelocity; }
    public void setAngularVelocity(float angularVelocity) { this.angularVelocity = angularVelocity; }

    public int getWorkMode() { return workMode; }
    public void setWorkMode(int workMode) { this.workMode = workMode; }
    public boolean isAutoMode() { return workMode == 3; }
    public boolean isManualMode() { return workMode == 1; }

    public int getAgvState() { return agvState; }
    public void setAgvState(int agvState) { this.agvState = agvState; }
    public boolean isIdle() { return agvState == 0; }
    public boolean isRunning() { return agvState == 1; }
    public boolean isPaused() { return agvState == 2; }
    public boolean isUninitialized() { return agvState == 3; }
    public boolean isNavFailed() { return agvState == 6; }

    public int getCapabilitySet() { return capabilitySet; }
    public void setCapabilitySet(int capabilitySet) { this.capabilitySet = capabilitySet; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public boolean hasTask() { return orderId != 0; }

    public int getTaskKey() { return taskKey; }
    public void setTaskKey(int taskKey) { this.taskKey = taskKey; }

    public float getBatteryPercent() { return batteryPercent; }
    public void setBatteryPercent(float batteryPercent) { this.batteryPercent = batteryPercent; }

    public float getBatteryVoltage() { return batteryVoltage; }
    public void setBatteryVoltage(float batteryVoltage) { this.batteryVoltage = batteryVoltage; }

    public float getBatteryCurrent() { return batteryCurrent; }
    public void setBatteryCurrent(float batteryCurrent) { this.batteryCurrent = batteryCurrent; }

    public int getChargeStatus() { return chargeStatus; }
    public void setChargeStatus(int chargeStatus) { this.chargeStatus = chargeStatus; }
    public boolean isCharging() { return chargeStatus == 1; }
    public boolean isFull() { return chargeStatus == 2; }

    public AbnormalEvent[] getAbnormalEvents() { return abnormalEvents; }
    public void setAbnormalEvents(AbnormalEvent[] abnormalEvents) { this.abnormalEvents = abnormalEvents; }

    public ActionStatus[] getActionStatuses() { return actionStatuses; }
    public void setActionStatuses(ActionStatus[] actionStatuses) { this.actionStatuses = actionStatuses; }

    public boolean hasError() {
        if (abnormalEvents == null) return false;
        for (AbnormalEvent e : abnormalEvents) {
            if (e.isError()) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("RobotStatus[pos=(%.2f,%.2f), heading=%.2frad, v=(%.2f,%.2f)m/s, "
                        + "mode=%d, state=%d, battery=%.0f%%, orderId=%d, taskKey=%d, loc=%d]",
                positionX, positionY, headingAngle, velocityX, velocityY,
                workMode, agvState, batteryPercent * 100, orderId, taskKey, localizationStatus);
    }
}
