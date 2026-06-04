package com.kecong.opentcs.protocol.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for RobotStatus navTaskState and related methods.
 */
@DisplayName("RobotStatus navTaskState")
class RobotStatusNavTaskTest {

    private RobotStatus status;

    @BeforeEach
    void setUp() {
        status = new RobotStatus();
    }

    @Test
    @DisplayName("Default navTaskState is 0")
    void testDefaultNavState() {
        assertEquals(0, status.getNavTaskState());
        assertFalse(status.isNavDone());
        assertFalse(status.isNavTaskFailed());
    }

    @Test
    @DisplayName("isNavDone returns true when taskState=4")
    void testIsNavDone() {
        status.setNavTaskState(4);
        assertTrue(status.isNavDone());
        assertFalse(status.isNavTaskFailed());
    }

    @Test
    @DisplayName("isNavTaskFailed returns true when taskState=5")
    void testIsNavTaskFailed() {
        status.setNavTaskState(5);
        assertFalse(status.isNavDone());
        assertTrue(status.isNavTaskFailed());
    }

    @Test
    @DisplayName("NavTaskState setter/getter round trip")
    void testNavTaskStateRoundTrip() {
        for (int ts : new int[]{0, 1, 2, 3, 4, 5, 6}) {
            status.setNavTaskState(ts);
            assertEquals(ts, status.getNavTaskState());
        }
    }

    @Test
    @DisplayName("isNavFailed still works for agvState=6")
    void testIsNavFailedAgvState() {
        status.setAgvState(6);
        assertTrue(status.isNavFailed());
        status.setAgvState(0);
        assertFalse(status.isNavFailed());
    }

    @Test
    @DisplayName("isIdle/isRunning/isPaused from agvState")
    void testAgvStateHelpers() {
        status.setAgvState(0);
        assertTrue(status.isIdle());
        assertFalse(status.isRunning());
        assertFalse(status.isPaused());

        status.setAgvState(1);
        assertFalse(status.isIdle());
        assertTrue(status.isRunning());
        assertFalse(status.isPaused());

        status.setAgvState(2);
        assertFalse(status.isIdle());
        assertFalse(status.isRunning());
        assertTrue(status.isPaused());
    }

    @Test
    @DisplayName("hasError detects error-level abnormal events")
    void testHasError() {
        assertFalse(status.hasError());

        RobotStatus.AbnormalEvent[] events = {
            new RobotStatus.AbnormalEvent(0x2004, 2)  // level=2 = error
        };
        status.setAbnormalEvents(events);
        assertTrue(status.hasError());

        events[0] = new RobotStatus.AbnormalEvent(0x1001, 1);  // level=1 = warning
        status.setAbnormalEvents(events);
        assertFalse(status.hasError());
    }
}
