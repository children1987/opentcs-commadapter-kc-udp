package com.kecong.opentcs.protocol.model;

import com.kecong.opentcs.protocol.KecongActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests to push NavigationTask builder coverage to 100%.
 */
@DisplayName("NavigationTask Edge Cases")
class NavigationTaskEdgeCaseTest {

    @Test
    @DisplayName("TaskPath.Builder - all fields including maxAngularSpeed")
    void testTaskPathBuilderAllFields() {
        NavigationTask.TaskPath path = NavigationTask.TaskPath.builder()
                .sequenceNumber(5)
                .pathId(999)
                .fixedAngle(1.57f)
                .fixedAngleEnabled(true)
                .travelPose(3)
                .maxSpeed(2.5f)
                .maxAngularSpeed(0.75f)
                .build();

        assertEquals(5, path.getSequenceNumber());
        assertEquals(999, path.getPathId());
        assertEquals(1.57f, path.getFixedAngle(), 0.01f);
        assertTrue(path.isFixedAngleEnabled());
        assertEquals(3, path.getTravelPose());
        assertEquals(2.5f, path.getMaxSpeed(), 0.01f);
        assertEquals(0.75f, path.getMaxAngularSpeed(), 0.01f);
        assertTrue(path.getActions().isEmpty());
    }

    @Test
    @DisplayName("TaskPath.Builder - null speeds")
    void testTaskPathBuilderNullSpeeds() {
        NavigationTask.TaskPath path = NavigationTask.TaskPath.builder()
                .sequenceNumber(1).pathId(100).build();

        assertNull(path.getMaxSpeed());
        assertNull(path.getMaxAngularSpeed());
        assertNull(path.getFixedAngle());
        assertFalse(path.isFixedAngleEnabled());
    }

    @Test
    @DisplayName("TaskPath.Builder - with actions")
    void testTaskPathBuilderWithActions() {
        NavigationTask.TaskAction action = new NavigationTask.TaskAction(
                KecongActionType.ACTION_PAUSE, (byte) 0, 1, new byte[0]);
        NavigationTask.TaskPath path = NavigationTask.TaskPath.builder()
                .sequenceNumber(1).pathId(100)
                .actions(java.util.Arrays.asList(action))
                .build();
        assertEquals(1, path.getActions().size());
        assertEquals(KecongActionType.ACTION_PAUSE, path.getActions().get(0).getActionType());
    }

    @Test
    @DisplayName("TaskPoint.Builder - all fields")
    void testTaskPointBuilderAllFields() {
        NavigationTask.TaskAction action = new NavigationTask.TaskAction(
                (short) 0x16, (byte) 1, 42, new byte[]{2, 0, 0, 0});

        NavigationTask.TaskPoint point = NavigationTask.TaskPoint.builder()
                .sequenceNumber(6)
                .pointId(777)
                .angle(3.14f)
                .specifyAngle(true)
                .actions(java.util.Arrays.asList(action))
                .build();

        assertEquals(6, point.getSequenceNumber());
        assertEquals(777, point.getPointId());
        assertEquals(3.14f, point.getAngle(), 0.01f);
        assertTrue(point.isSpecifyAngle());
        assertEquals(1, point.getActions().size());
        assertEquals((short) 0x16, point.getActions().get(0).getActionType());
    }

    @Test
    @DisplayName("TaskPoint.Builder - without angle")
    void testTaskPointBuilderNoAngle() {
        NavigationTask.TaskPoint point = NavigationTask.TaskPoint.builder()
                .sequenceNumber(2).pointId(50).build();
        assertNull(point.getAngle());
        assertFalse(point.isSpecifyAngle());
    }

    @Test
    @DisplayName("NavigationTask.Builder - all fields")
    void testNavigationTaskBuilderAllFields() {
        NavigationTask.TaskPoint p1 = NavigationTask.TaskPoint.builder()
                .sequenceNumber(0).pointId(1).build();
        NavigationTask.TaskPoint p2 = NavigationTask.TaskPoint.builder()
                .sequenceNumber(2).pointId(2).build();
        NavigationTask.TaskPath path = NavigationTask.TaskPath.builder()
                .sequenceNumber(1).pathId(10).build();

        NavigationTask task = NavigationTask.builder()
                .orderId(42)
                .taskKey(7)
                .navigationMode(NavigationTask.NAV_MODE_TARGET_POINT)
                .points(java.util.Arrays.asList(p1, p2))
                .paths(java.util.Arrays.asList(path))
                .build();

        assertEquals(42, task.getOrderId());
        assertEquals(7, task.getTaskKey());
        assertEquals(NavigationTask.NAV_MODE_TARGET_POINT, task.getNavigationMode());
        assertEquals(2, task.getPoints().size());
        assertEquals(1, task.getPaths().size());
    }

    @Test
    @DisplayName("NavigationTask.Builder - free navigation mode")
    void testNavigationTaskFreeMode() {
        NavigationTask task = NavigationTask.builder()
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .build();
        assertEquals(NavigationTask.NAV_MODE_FREE, task.getNavigationMode());
    }

    @Test
    @DisplayName("TaskAction - params defensive copy verification")
    void testTaskActionParamsDefensive() {
        byte[] params = new byte[]{1, 2, 3};
        NavigationTask.TaskAction action = new NavigationTask.TaskAction(
                (short) 1, (byte) 0, 1, params);

        // Modify original - should not affect action
        params[0] = 99;
        assertEquals(1, action.getParams()[0]);

        // Modify from getter - should not affect action
        byte[] gotten = action.getParams();
        gotten[1] = 88;
        assertEquals(2, action.getParams()[1]);
    }

    @Test
    @DisplayName("TaskAction - null params in constructor")
    void testTaskActionNullParams() {
        NavigationTask.TaskAction action = new NavigationTask.TaskAction(
                (short) 1, (byte) 0, 1, null);
        assertEquals(0, action.getParams().length);
    }
}
