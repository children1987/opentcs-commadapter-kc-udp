package com.kecong.opentcs.protocol;

import com.kecong.opentcs.protocol.model.NavigationTask;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskAction;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPath;
import com.kecong.opentcs.protocol.model.NavigationTask.TaskPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case tests for KecongMessageEncoder to push coverage to 100%.
 */
@DisplayName("KecongMessageEncoder Edge Cases")
class KecongMessageEncoderEdgeCaseTest {

    @Test
    @DisplayName("Encode nav task - path splice with path segments")
    void testEncodeTaskWithPathsAndActions() {
        TaskAction action = new TaskAction(
                KecongActionType.ACTION_PALLET_LIFT,
                KecongActionType.CONCURRENT_SINGLE,
                99,
                new byte[]{1, 0, 0, 0});

        NavigationTask task = NavigationTask.builder()
                .orderId(3).taskKey(3)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE)
                .addPoint(TaskPoint.builder().sequenceNumber(0).pointId(10).build())
                .addPoint(TaskPoint.builder().sequenceNumber(2).pointId(20).build())
                .addPath(TaskPath.builder().sequenceNumber(1).pathId(50)
                        .travelPose(2).maxSpeed(1.0f).maxAngularSpeed(0.5f)
                        .fixedAngle(0.5f).fixedAngleEnabled(true)
                        .addAction(action).build())
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
        assertTrue(data.length <= 512);
    }

    @Test
    @DisplayName("Encode nav task - free navigation with angle specified")
    void testEncodeTaskFreeModeSpecifyAngle() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .addPoint(TaskPoint.builder().sequenceNumber(0).pointId(99)
                        .angle(2.0f).specifyAngle(true).build())
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("Encode nav task - free navigation with action")
    void testEncodeTaskFreeModeWithAction() {
        TaskAction action = new TaskAction(
                KecongActionType.ACTION_CANCEL,
                KecongActionType.CONCURRENT_ALL,
                10,
                new byte[]{5, 0, 0, 0, 1, 0, 0, 0});

        NavigationTask task = NavigationTask.builder()
                .orderId(5).taskKey(2)
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .addPoint(TaskPoint.builder().sequenceNumber(0).pointId(42)
                        .angle(0f).specifyAngle(false)
                        .addAction(action).build())
                .build();

        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("Encode immediate action - fork lift")
    void testEncodeImmediateActionForkLift() {
        byte[] params = new byte[]{0, 0, 0x40, 0x40, 1, 2, 0, 0};
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                KecongActionType.ACTION_FORK_LIFT,
                KecongActionType.CONCURRENT_ACTION_ONLY,
                777,
                params);
        assertNotNull(data);
        assertTrue(data.length >= 12);
    }

    @Test
    @DisplayName("Encode subscription with change report enabled")
    void testEncodeSubscriptionChangeReport() {
        byte[] commands = new byte[]{
                KecongCommandCode.CMD_QUERY_ROBOT_STATUS,
                KecongCommandCode.CMD_QUERY_CARGO_STATUS
        };
        byte[] data = KecongMessageEncoder.encodeSubscription(
                commands, 200, 120000, true, "change-report-uuid");
        assertNotNull(data);
        assertEquals(0x80 + 64, data.length);
    }
}
