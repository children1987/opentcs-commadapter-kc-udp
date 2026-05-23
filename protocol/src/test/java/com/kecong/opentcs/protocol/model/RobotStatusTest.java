package com.kecong.opentcs.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RobotStatus model.
 */
@DisplayName("RobotStatus")
class RobotStatusTest {

    @Test
    @DisplayName("Default state checks")
    void testDefaultState() {
        RobotStatus status = new RobotStatus();

        // Default work mode should not be auto
        assertFalse(status.isAutoMode());
        assertFalse(status.isManualMode());

        // Default AGV state
        assertTrue(status.isIdle());
        assertFalse(status.isRunning());
        assertFalse(status.isPaused());
        assertFalse(status.isNavFailed());

        // Default task
        assertFalse(status.hasTask());

        // Default battery
        assertFalse(status.isCharging());
        assertFalse(status.isFull());

        // No errors
        assertFalse(status.hasError());
    }

    @Test
    @DisplayName("Setters and getters")
    void testSettersAndGetters() {
        RobotStatus status = new RobotStatus();

        status.setPositionX(10.5f);
        status.setPositionY(20.3f);
        status.setHeadingAngle(1.57f);
        status.setVelocityX(1.0f);
        status.setVelocityY(0.5f);
        status.setAngularVelocity(0.2f);
        status.setWorkMode(3);
        status.setAgvState(1);
        status.setOrderId(42);
        status.setTaskKey(3);
        status.setBatteryPercent(0.75f);
        status.setChargeStatus(1);
        status.setConfidence(85);
        status.setLocalizationStatus(3);
        status.setCapabilitySet(1);

        assertEquals(10.5f, status.getPositionX(), 0.01f);
        assertEquals(20.3f, status.getPositionY(), 0.01f);
        assertEquals(1.57f, status.getHeadingAngle(), 0.01f);
        assertEquals(1.0f, status.getVelocityX(), 0.01f);
        assertEquals(0.5f, status.getVelocityY(), 0.01f);
        assertEquals(0.2f, status.getAngularVelocity(), 0.01f);
        assertTrue(status.isAutoMode());
        assertTrue(status.isRunning());
        assertEquals(42, status.getOrderId());
        assertTrue(status.hasTask());
        assertEquals(3, status.getTaskKey());
        assertEquals(0.75f, status.getBatteryPercent(), 0.01f);
        assertTrue(status.isCharging());
        assertEquals(85, status.getConfidence());
        assertEquals(3, status.getLocalizationStatus());
        assertEquals(1, status.getCapabilitySet());
    }

    @Test
    @DisplayName("Paused state check")
    void testPausedState() {
        RobotStatus status = new RobotStatus();
        status.setAgvState(2);
        assertTrue(status.isPaused());
    }

    @Test
    @DisplayName("Uninitialized state check")
    void testUninitializedState() {
        RobotStatus status = new RobotStatus();
        status.setAgvState(3);
        assertTrue(status.isUninitialized());
    }

    @Test
    @DisplayName("AbnormalEvent type checks")
    void testAbnormalEvent() {
        RobotStatus.AbnormalEvent error = new RobotStatus.AbnormalEvent(0x0108, 2);
        assertTrue(error.isError());
        assertFalse(error.isWarning());
        assertEquals(0x0108, error.getEventCode());
        assertEquals(2, error.getLevel());

        RobotStatus.AbnormalEvent warning = new RobotStatus.AbnormalEvent(0x0113, 1);
        assertFalse(warning.isError());
        assertTrue(warning.isWarning());

        RobotStatus.AbnormalEvent info = new RobotStatus.AbnormalEvent(0x0005, 0);
        assertFalse(info.isError());
        assertFalse(info.isWarning());
    }

    @Test
    @DisplayName("ActionStatus checks")
    void testActionStatus() {
        RobotStatus.ActionStatus complete = new RobotStatus.ActionStatus(1, 3);
        assertTrue(complete.isComplete());
        assertFalse(complete.isFailed());
        assertFalse(complete.isExecuting());

        RobotStatus.ActionStatus failed = new RobotStatus.ActionStatus(2, 4);
        assertTrue(failed.isFailed());

        RobotStatus.ActionStatus executing = new RobotStatus.ActionStatus(3, 2);
        assertTrue(executing.isExecuting());
    }

    @Test
    @DisplayName("hasError with null events array")
    void testHasErrorNullEvents() {
        RobotStatus status = new RobotStatus();
        status.setAbnormalEvents(null);
        assertFalse(status.hasError());
    }

    @Test
    @DisplayName("hasError with mixed events")
    void testHasErrorMixed() {
        RobotStatus status = new RobotStatus();
        status.setAbnormalEvents(new RobotStatus.AbnormalEvent[]{
                new RobotStatus.AbnormalEvent(1, 1),  // warning
                new RobotStatus.AbnormalEvent(2, 2),  // error
        });
        assertTrue(status.hasError());
    }

    @Test
    @DisplayName("toString includes key fields")
    void testToString() {
        RobotStatus status = new RobotStatus();
        status.setPositionX(1.0f);
        status.setPositionY(2.0f);
        status.setHeadingAngle(0.5f);
        status.setVelocityX(1.0f);
        status.setVelocityY(0f);
        status.setBatteryPercent(0.85f);
        status.setOrderId(5);
        status.setTaskKey(2);

        String s = status.toString();
        assertTrue(s.contains("1.00"));
        assertTrue(s.contains("2.00"));
        assertTrue(s.contains("85"));
        assertTrue(s.contains("5"));
    }
}
