package com.kecong.opentcs.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NavigationTask model and its nested builder classes.
 */
@DisplayName("NavigationTask")
class NavigationTaskTest {

    @Test
    @DisplayName("Build simple navigation task")
    void testBuildSimpleTask() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1)
                .taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE)
                .addPoint(NavigationTask.TaskPoint.builder().sequenceNumber(0).pointId(5).build())
                .addPoint(NavigationTask.TaskPoint.builder().sequenceNumber(2).pointId(10).build())
                .build();

        assertEquals(1, task.getOrderId());
        assertEquals(1, task.getTaskKey());
        assertEquals(NavigationTask.NAV_MODE_PATH_SPLICE, task.getNavigationMode());
        assertEquals(2, task.getPoints().size());
        assertEquals(0, task.getPaths().size());
    }

    @Test
    @DisplayName("Task with path segments")
    void testTaskWithPaths() {
        NavigationTask task = NavigationTask.builder()
                .orderId(2)
                .taskKey(3)
                .addPoint(NavigationTask.TaskPoint.builder().sequenceNumber(0).pointId(100).build())
                .addPath(NavigationTask.TaskPath.builder()
                        .sequenceNumber(1)
                        .pathId(200)
                        .travelPose(0)
                        .maxSpeed(1.5f)
                        .build())
                .build();

        assertEquals(1, task.getPaths().size());
        NavigationTask.TaskPath path = task.getPaths().get(0);
        assertEquals(1, path.getSequenceNumber());
        assertEquals(200, path.getPathId());
        assertEquals(0, path.getTravelPose());
        assertEquals(1.5f, path.getMaxSpeed(), 0.01f);
    }

    @Test
    @DisplayName("Task point with angle specification")
    void testPointWithAngle() {
        NavigationTask.TaskPoint point = NavigationTask.TaskPoint.builder()
                .sequenceNumber(0)
                .pointId(42)
                .angle(1.57f)
                .specifyAngle(true)
                .build();

        assertEquals(0, point.getSequenceNumber());
        assertEquals(42, point.getPointId());
        assertEquals(1.57f, point.getAngle(), 0.01f);
        assertTrue(point.isSpecifyAngle());
    }

    @Test
    @DisplayName("Task point without angle")
    void testPointWithoutAngle() {
        NavigationTask.TaskPoint point = NavigationTask.TaskPoint.builder()
                .sequenceNumber(2)
                .pointId(30)
                .build();

        assertNull(point.getAngle());
        assertFalse(point.isSpecifyAngle());
        assertEquals(2, point.getSequenceNumber());
        assertEquals(30, point.getPointId());
    }

    @Test
    @DisplayName("Task action construction")
    void testTaskAction() {
        byte[] params = new byte[]{1, 0, 0, 0};
        NavigationTask.TaskAction action = new NavigationTask.TaskAction(
                (short) 0x12, (byte) 0x02, 100, params);

        assertEquals((short) 0x12, action.getActionType());
        assertEquals((byte) 0x02, action.getConcurrencyMode());
        assertEquals(100, action.getActionId());
        assertArrayEquals(params, action.getParams());

        // Verify params is a defensive copy
        params[0] = 99;
        assertNotEquals(99, action.getParams()[0]);
    }

    @Test
    @DisplayName("Task path with all fields")
    void testPathAllFields() {
        NavigationTask.TaskPath path = NavigationTask.TaskPath.builder()
                .sequenceNumber(3)
                .pathId(500)
                .fixedAngle(0.78f)
                .fixedAngleEnabled(true)
                .travelPose(1)  // reverse
                .maxSpeed(2.0f)
                .maxAngularSpeed(0.5f)
                .build();

        assertEquals(3, path.getSequenceNumber());
        assertEquals(500, path.getPathId());
        assertEquals(0.78f, path.getFixedAngle(), 0.01f);
        assertTrue(path.isFixedAngleEnabled());
        assertEquals(1, path.getTravelPose());
        assertEquals(2.0f, path.getMaxSpeed(), 0.01f);
        assertEquals(0.5f, path.getMaxAngularSpeed(), 0.01f);
    }

    @Test
    @DisplayName("Unmodifiable point/action lists")
    void testUnmodifiableLists() {
        NavigationTask task = NavigationTask.builder()
                .addPoint(NavigationTask.TaskPoint.builder().sequenceNumber(0).pointId(1).build())
                .build();

        assertThrows(UnsupportedOperationException.class, () -> task.getPoints().add(null));
    }

    @Test
    @DisplayName("Builder defaults")
    void testBuilderDefaults() {
        NavigationTask task = NavigationTask.builder().build();
        assertEquals(1, task.getOrderId());
        assertEquals(1, task.getTaskKey());
        assertEquals(NavigationTask.NAV_MODE_PATH_SPLICE, task.getNavigationMode());
    }

    @Test
    @DisplayName("Navigation mode constants")
    void testNavigationModes() {
        assertEquals(0, NavigationTask.NAV_MODE_PATH_SPLICE);
        assertEquals(1, NavigationTask.NAV_MODE_FREE);
        assertEquals(2, NavigationTask.NAV_MODE_TARGET_POINT);
    }

    @Test
    @DisplayName("Point with actions")
    void testPointWithActions() {
        NavigationTask.TaskAction action = new NavigationTask.TaskAction(
                (short) 0x16, (byte) 0x02, 200, new byte[]{1, 0, 0, 0});

        NavigationTask.TaskPoint point = NavigationTask.TaskPoint.builder()
                .sequenceNumber(0)
                .pointId(10)
                .addAction(action)
                .build();

        assertEquals(1, point.getActions().size());
        assertEquals((short) 0x16, point.getActions().get(0).getActionType());
    }
}
