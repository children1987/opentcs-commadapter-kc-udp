package com.kecong.opentcs.protocol.model;

import com.kecong.opentcs.protocol.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全覆盖冲刺：补齐 Encoder 新方法 + MagneticStatus 全部谓词 + 各小缺口。
 */
@DisplayName("Coverage Sprint to 100%")
class CoverageSprintTest {

    // ========== KecongMessageEncoder — 新协议方法 ==========

    @Test
    @DisplayName("encodeTrafficResult — non-null")
    void testEncodeTrafficResult() {
        TrafficResource res = TrafficResource.newResult(true)
                .addPath(10, 0)
                .build();
        byte[] data = KecongMessageEncoder.encodeTrafficResult(res);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("encodeTrafficResult — null returns empty")
    void testEncodeTrafficResultNull() {
        byte[] data = KecongMessageEncoder.encodeTrafficResult(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeQrNavTask — non-null (F1)")
    void testEncodeQrNavTask() {
        QrNavigationTask.Segment seg = new QrNavigationTask.Segment.Builder(100, 1).build();
        QrNavigationTask task = QrNavigationTask.builder()
                .totalSegmentCount(1)
                .addSegment(seg).build();
        byte[] data = KecongMessageEncoder.encodeQrNavTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("encodeQrNavTask — null returns empty")
    void testEncodeQrNavTaskNull() {
        byte[] data = KecongMessageEncoder.encodeQrNavTask(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeQrLongPathTask — non-null (F5)")
    void testEncodeQrLongPathTask() {
        QrNavigationTask.Segment seg = new QrNavigationTask.Segment.Builder(200, 2).build();
        QrNavigationTask task = QrNavigationTask.builder()
                .totalSegmentCount(1)
                .addSegment(seg).build();
        byte[] data = KecongMessageEncoder.encodeQrLongPathTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("encodeQrLongPathTask — null returns empty")
    void testEncodeQrLongPathTaskNull() {
        byte[] data = KecongMessageEncoder.encodeQrLongPathTask(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeMagneticTask — non-null")
    void testEncodeMagneticTask() {
        MagneticNavTask task = MagneticNavTask.builder()
                .totalSegmentCount(1)
                .addLandmark(MagneticNavTask.Landmark.builder(1).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeMagneticTask(task);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("encodeMagneticTask — null returns empty")
    void testEncodeMagneticTaskNull() {
        byte[] data = KecongMessageEncoder.encodeMagneticTask(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeMagneticControl — non-null")
    void testEncodeMagneticControl() {
        MagneticNavTask.MagneticControl ctrl =
                new MagneticNavTask.MagneticControl(
                        MagneticNavTask.MagneticControl.CTRL_START, true);
        byte[] data = KecongMessageEncoder.encodeMagneticControl(ctrl);
        assertNotNull(data);
        assertEquals(4, data.length);
    }

    @Test
    @DisplayName("encodeMagneticControl — null returns empty")
    void testEncodeMagneticControlNull() {
        byte[] data = KecongMessageEncoder.encodeMagneticControl(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeMagneticRelocalize — non-null")
    void testEncodeMagneticRelocalize() {
        MagneticNavTask.MagneticRelocalize reloc = new MagneticNavTask.MagneticRelocalize(
                1, (short) 90, 50f, 10,
                MagneticNavTask.MagneticRelocalize.SEG_TYPE_MAGNETIC_TAPE);
        byte[] data = KecongMessageEncoder.encodeMagneticRelocalize(reloc);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("encodeMagneticRelocalize — null returns empty")
    void testEncodeMagneticRelocalizeNull() {
        byte[] data = KecongMessageEncoder.encodeMagneticRelocalize(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    // ========== KecongMessageEncoder — encodeImmediateAction more paths ==========

    @Test
    @DisplayName("encodeImmediateAction — params with actual content")
    void testEncodeImmediateActionWithParams() {
        byte[] params = new byte[]{0x01, 0, 0, 0, 0x01, 0, 0, 0};
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                (short) 0x16, (byte) 0, 99, params);
        assertNotNull(data);
        assertTrue(data.length >= 12);
    }

    @Test
    @DisplayName("encodeImmediateAction — empty params array")
    void testEncodeImmediateActionEmptyParams() {
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                (short) 0x12, (byte) 0, 101, new byte[0]);
        assertNotNull(data);
        assertTrue(data.length >= 12);
    }

    // ========== KecongMessageDecoder — 新方法 ==========

    @Test
    @DisplayName("decodeTrafficRequest — valid data with 2 paths")
    void testDecodeTrafficRequest() {
        ByteBuffer buf = ByteBuffer.allocate(4 + 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);  // hasRequest=true
        buf.put((byte) 2);  // pathCount=2
        buf.putShort((short) 0); // reserved
        buf.putShort((short) 100); buf.putShort((short) 0); // pathId=100, endpointId=0
        buf.putShort((short) 200); buf.putShort((short) 1); // pathId=200, endpointId=1

        TrafficResource res = KecongMessageDecoder.decodeTrafficRequest(buf.array());
        assertNotNull(res);
        assertTrue(res.hasRequest());
        assertEquals(2, res.getPathCount());
        assertEquals(100, res.getPaths().get(0).getPathId());
        assertEquals(200, res.getPaths().get(1).getPathId());
    }

    @Test
    @DisplayName("decodeTrafficRequest — no request")
    void testDecodeTrafficRequestNoRequest() {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0);  // hasRequest=false
        buf.put((byte) 0);  // pathCount=0
        buf.putShort((short) 0);

        TrafficResource res = KecongMessageDecoder.decodeTrafficRequest(buf.array());
        assertNotNull(res);
        assertFalse(res.hasRequest());
        assertEquals(0, res.getPathCount());
    }

    @Test
    @DisplayName("decodeQrLongPathTaskResponse — valid")
    void testDecodeQrLongPathTaskResponse() {
        ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 5);    // totalSegmentCount
        buf.put((byte) 1);          // angleLimitEnabled
        buf.put((byte) 45);         // angleLimit
        buf.put(new byte[8]);       // reserved

        QrNavigationTask task = KecongMessageDecoder.decodeQrLongPathTaskResponse(buf.array());
        assertNotNull(task);
        assertEquals(5, task.getTotalSegmentCount());
        assertTrue(task.isAngleLimitEnabled());
        assertEquals(45, task.getAngleLimit());
    }

    @Test
    @DisplayName("decodeMagneticStatus — valid 20-byte")
    void testDecodeMagneticStatus() {
        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0);  // run normal
        buf.put((byte) 0);  // reserved
        buf.put((byte) 2);  // nav executing
        buf.put((byte) 1);  // landmark detected
        buf.putShort((short) 42);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.putFloat(100f);
        buf.putShort((short) 90);
        buf.put((byte) 1);
        buf.put((byte) 0);
        buf.putShort((short) 0);
        buf.putShort((short) 0);

        MagneticNavTask.MagneticStatus s = KecongMessageDecoder.decodeMagneticStatus(buf.array());
        assertNotNull(s);
        assertEquals(2, s.getNavStatus());
    }

    // ========== MagneticStatus — 全部谓词（10种状态组合） ==========

    private MagneticNavTask.MagneticStatus buildStatus(int runStatus, int navStatus,
                                                        boolean landmark, boolean trafficWait,
                                                        int positioningState) {
        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) runStatus);
        buf.put((byte) 0); // reserved
        buf.put((byte) navStatus);
        buf.put((byte) (landmark ? 1 : 0));
        buf.putShort((short) 0);
        buf.put((byte) 0); // navAllowed
        buf.put((byte) (trafficWait ? 1 : 0));
        buf.putFloat(0);
        buf.putShort((short) 0);
        buf.put((byte) 1); // headingValid
        buf.put((byte) positioningState);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        return MagneticNavTask.fromE2Response(buf.array());
    }

    @Test
    @DisplayName("MagneticStatus — idle (nav=0)")
    void testMagStatusIdle() {
        MagneticNavTask.MagneticStatus s = buildStatus(0, 0, false, false, 0);
        assertFalse(s.isRunning());
        assertFalse(s.isPaused());
        assertFalse(s.isArrived());
        assertFalse(s.isFault());
        assertFalse(s.isDerailed());
        assertTrue(s.isLocated());  // positioning=0
        assertFalse(s.isLandmarkDetected());
        assertFalse(s.isTrafficControlWait());
        assertEquals(0, s.getRunStatus());
        assertEquals(0, s.getNavStatus());
    }

    @Test
    @DisplayName("MagneticStatus — paused (nav=1)")
    void testMagStatusPaused() {
        MagneticNavTask.MagneticStatus s = buildStatus(0, 1, true, false, 0);
        assertTrue(s.isPaused());
        assertFalse(s.isRunning());
        assertFalse(s.isArrived());
        assertFalse(s.isFault());
        assertTrue(s.isLandmarkDetected());
    }

    @Test
    @DisplayName("MagneticStatus — executing (nav=2)")
    void testMagStatusExecuting() {
        MagneticNavTask.MagneticStatus s = buildStatus(0, 2, true, true, 0);
        assertTrue(s.isRunning());
        assertFalse(s.isPaused());
        assertTrue(s.isTrafficControlWait());
    }

    @Test
    @DisplayName("MagneticStatus — cancelled (nav=3)")
    void testMagStatusCancelled() {
        MagneticNavTask.MagneticStatus s = buildStatus(0, 3, false, false, 0);
        assertEquals(3, s.getNavStatus());
        assertFalse(s.isRunning());
        assertFalse(s.isPaused());
        assertFalse(s.isArrived());
    }

    @Test
    @DisplayName("MagneticStatus — completed (nav=4)")
    void testMagStatusCompleted() {
        MagneticNavTask.MagneticStatus s = buildStatus(0, 4, false, false, 3);
        assertTrue(s.isArrived());
        assertTrue(s.isLocated());  // positioning=3
        assertFalse(s.isRunning());
    }

    @Test
    @DisplayName("MagneticStatus — landmark fault (run=1)")
    void testMagStatusLandmarkFault() {
        MagneticNavTask.MagneticStatus s = buildStatus(1, 2, false, false, 1);
        assertTrue(s.isFault());
        assertFalse(s.isDerailed());
        assertFalse(s.isLocated());  // positioning=1 (need reloc)
    }

    @Test
    @DisplayName("MagneticStatus — derail (run=2)")
    void testMagStatusDerail() {
        MagneticNavTask.MagneticStatus s = buildStatus(2, 2, false, false, 2);
        assertTrue(s.isDerailed());
        assertTrue(s.isFault());
        assertFalse(s.isLocated());  // positioning=2 (locating)
    }

    @Test
    @DisplayName("MagneticStatus — arc turn (run=5)")
    void testMagStatusArcTurn() {
        MagneticNavTask.MagneticStatus s = buildStatus(5, 2, false, false, 0);
        assertTrue(s.isFault());
        assertFalse(s.isDerailed());
        assertEquals(5, s.getRunStatus());
    }

    @Test
    @DisplayName("MagneticStatus — right angle (run=6)")
    void testMagStatusRightAngle() {
        MagneticNavTask.MagneticStatus s = buildStatus(6, 2, false, false, 0);
        assertTrue(s.isFault());
        assertEquals(6, s.getRunStatus());
    }

    @Test
    @DisplayName("MagneticStatus — unknown (run=0xFF)")
    void testMagStatusUnknown() {
        MagneticNavTask.MagneticStatus s = buildStatus(0xFF, 2, false, false, 0);
        assertTrue(s.isFault());
        assertEquals(0xFF, s.getRunStatus());
    }

    // ========== KecongProtocolFrame — edge case ==========

    @Test
    @DisplayName("KecongProtocolFrame — data too short for header")
    void testProtocolFrameTooShort() {
        assertThrows(IllegalArgumentException.class, () ->
                KecongProtocolFrame.decode(new byte[10]));
    }

    // ========== QrNavigationTask — F1/F5 encoding + fromF5Response ==========

    @Test
    @DisplayName("QrNavigationTask — encode F1 simple QR nav task")
    void testQrNavTaskF1Encode() {
        QrNavigationTask.Segment seg = new QrNavigationTask.Segment.Builder(123, 1).build();
        QrNavigationTask task = QrNavigationTask.builder()
                .addSegment(seg).build();

        byte[] data = task.toF1Bytes();
        assertNotNull(data);
        assertEquals(8 + 16, data.length); // header(8) + 1 segment(16)
    }

    @Test
    @DisplayName("QrNavigationTask — encode F5 long-path task")
    void testQrNavTaskF5Encode() {
        QrNavigationTask.Segment seg = new QrNavigationTask.Segment.Builder(456, 2).build();
        QrNavigationTask task = QrNavigationTask.builder()
                .totalSegmentCount(10).segmentOffset(5)
                .addSegment(seg).build();

        byte[] data = task.toF5Bytes();
        assertNotNull(data);
        assertEquals(16 + 16, data.length); // header(16) + 1 segment(16)
    }

    @Test
    @DisplayName("QrNavigationTask — fromF5Response")
    void testQrNavTaskFromF5() {
        ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 7);    // totalSegmentCount
        buf.put((byte) 1);          // angleLimitEnabled
        buf.put((byte) 90);         // angleLimit
        buf.put(new byte[8]);       // reserved

        QrNavigationTask task = QrNavigationTask.fromF5Response(buf.array());
        assertNotNull(task);
        assertEquals(7, task.getTotalSegmentCount());
        assertTrue(task.isAngleLimitEnabled());
        assertEquals(90, task.getAngleLimit());
    }

    @Test
    @DisplayName("QrNavigationTask — fromF5Response null/invalid")
    void testQrNavTaskFromF5Invalid() {
        assertNull(QrNavigationTask.fromF5Response(null));
        assertNull(QrNavigationTask.fromF5Response(new byte[5]));
    }

    // ========== TrafficResource — edge cases ==========

    @Test
    @DisplayName("TrafficResource — fromQueryResponse valid 2 paths")
    void testTrafficResourceFromQuery() {
        ByteBuffer buf = ByteBuffer.allocate(4 + 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);  // hasRequest
        buf.put((byte) 2);  // pathCount
        buf.putShort((short) 0); // reserved
        buf.putShort((short) 100); buf.putShort((short) 0);   // pathId=100, endpoint=0
        buf.putShort((short) 200); buf.putShort((short) 1);   // pathId=200, endpoint=1

        TrafficResource res = TrafficResource.fromQueryResponse(buf.array());
        assertNotNull(res);
        assertTrue(res.hasRequest());
        assertEquals(2, res.getPathCount());
        assertEquals(100, res.getPaths().get(0).getPathId());
        assertEquals(0, res.getPaths().get(0).getEndpointId());
    }

    @Test
    @DisplayName("TrafficResource — fromQueryResponse null/short data")
    void testTrafficResourceFromQueryInvalid() {
        TrafficResource resNull = TrafficResource.fromQueryResponse(null);
        assertNotNull(resNull);
        assertFalse(resNull.hasRequest());

        TrafficResource resShort = TrafficResource.fromQueryResponse(new byte[2]);
        assertNotNull(resShort);
        assertFalse(resShort.hasRequest());
    }

    @Test
    @DisplayName("TrafficResource — newResult with success / fail")
    void testTrafficResourceResult() {
        TrafficResource ok = TrafficResource.newResult(true)
                .addPath(10, 5)
                .build();
        assertTrue(ok.isSuccess());
        assertFalse(ok.hasRequest());
        assertEquals(1, ok.getPathCount());

        byte[] data = ok.toNotifyBytes();
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("TrafficResource — newResult with failure")
    void testTrafficResourceResultFail() {
        TrafficResource fail = TrafficResource.newResult(false).build();
        assertFalse(fail.isSuccess());
        assertEquals(0, fail.getPathCount());
    }

    @Test
    @DisplayName("TrafficResource — toString")
    void testTrafficResourceToString() {
        TrafficResource res = TrafficResource.newResult(true).build();
        assertTrue(res.toString().contains("TrafficResource"));
    }

    @Test
    @DisplayName("TrafficResource.Path — toString")
    void testTrafficPathToString() {
        TrafficResource.Path p = new TrafficResource.Path(42, 7);
        assertTrue(p.toString().contains("42"));
        assertTrue(p.toString().contains("7"));
    }

    // ========== MagneticRelocalize — toString nails ==========

    @Test
    @DisplayName("MagneticRelocalize — toString for nail type")
    void testRelocalizeToStringNail() {
        MagneticNavTask.MagneticRelocalize reloc = new MagneticNavTask.MagneticRelocalize(
                3, (short) -45, 25f, 50, 1); // nail
        String s = reloc.toString();
        assertTrue(s.contains("nail"));
        assertTrue(s.contains("3"));
        assertTrue(s.contains("-45"));
    }

    // ========== MagneticControl — all types ==========

    @Test
    @DisplayName("MagneticControl — all control types and toString")
    void testMagneticControlAllTypes() {
        int[] types = {0, 1, 2, 3, 6};
        for (int type : types) {
            MagneticNavTask.MagneticControl ctrl =
                    new MagneticNavTask.MagneticControl(type, false);
            assertEquals(type, ctrl.getControlType());
            assertFalse(ctrl.isTrafficMgmtEnabled());
            byte[] data = ctrl.toBytes();
            assertEquals(4, data.length);
            assertEquals(type, data[0] & 0xFF);
            assertTrue(ctrl.toString().contains(String.valueOf(type)));
        }
    }

    @Test
    @DisplayName("MagneticControl — with traffic mgmt enabled")
    void testMagneticControlWithTraffic() {
        MagneticNavTask.MagneticControl ctrl =
                new MagneticNavTask.MagneticControl(3, true);
        assertTrue(ctrl.isTrafficMgmtEnabled());
        byte[] data = ctrl.toBytes();
        assertEquals(1, data[1] & 0xFF);
    }

    // ========== RobotStatus — predicate completeness ==========

    @Test
    @DisplayName("RobotStatus — all AGV state predicates (true and false)")
    void testRobotStatusStatePredicates() {
        RobotStatus s = new RobotStatus();

        // idle
        s.setAgvState(0);
        assertTrue(s.isIdle());
        assertFalse(s.isRunning());
        assertFalse(s.isPaused());

        // running
        s.setAgvState(1);
        assertTrue(s.isRunning());
        assertFalse(s.isIdle());
        assertFalse(s.isUninitialized());

        // paused
        s.setAgvState(2);
        assertTrue(s.isPaused());

        // uninitialized
        s.setAgvState(3);
        assertTrue(s.isUninitialized());

        // nav failed
        s.setAgvState(6);
        assertTrue(s.isNavFailed());
    }

    // ========== Decoder — null/empty for all methods ==========

    @Test
    @DisplayName("decodeCargoStatus — null/empty return false")
    void testDecodeCargoStatusEdge() {
        assertFalse(KecongMessageDecoder.decodeCargoStatus(new byte[0]));
        assertFalse(KecongMessageDecoder.decodeCargoStatus(null));
    }

    @Test
    @DisplayName("KecongProtocolFrame — decode valid frame")
    void testProtocolFrameDecodeValid() {
        // header(28) + data(4) = 32 bytes total
        byte[] raw = new byte[28 + 4];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[16]);           // auth
        buf.put((byte) 1); buf.put((byte) 0);  // version + msgType
        buf.putShort((short) 0);         // seq
        buf.put((byte) 0x10); buf.put((byte) 0);  // service + cmd
        buf.put((byte) 0); buf.put((byte) 0);  // exec + reserved
        buf.putShort((short) 4);         // dataLength
        buf.putShort((short) 0);         // reserved
        buf.put(new byte[4]);            // data payload

        assertDoesNotThrow(() -> KecongProtocolFrame.decode(raw));
    }

    // ========== Final tiny gaps ==========

    @Test
    @DisplayName("MagneticNavTask — all getters exercised")
    void testMagneticNavTaskAllGetters() {
        MagneticNavTask task = MagneticNavTask.builder()
                .totalSegmentCount(5).totalTagmarkCount(3).stopDistance(20)
                .addLandmark(MagneticNavTask.Landmark.builder(1).build())
                .build();
        assertEquals(5, task.getTotalSegmentCount());
        assertEquals(3, task.getTotalTagmarkCount());
        assertEquals(20, task.getStopDistance());
    }

    @Test
    @DisplayName("MagneticStatus — getPositioningState, getWaitingSegmentId, getPathStartPointId")
    void testMagneticStatusRemainingGetters() {
        ByteBuffer buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0); buf.put((byte) 0);
        buf.put((byte) 2);  // executing
        buf.put((byte) 1);  // landmark detected
        buf.putShort((short) 50);   // currentSegmentId
        buf.put((byte) 0); buf.put((byte) 0);
        buf.putFloat(0);
        buf.putShort((short) 90);
        buf.put((byte) 1);
        buf.put((byte) 0);  // positioningState = success
        buf.putShort((short) 99);   // waitingSegmentId
        buf.putShort((short) 77);   // pathStartPointId

        MagneticNavTask.MagneticStatus s = MagneticNavTask.fromE2Response(buf.array());
        assertEquals(0, s.getPositioningState());
        assertEquals(99, s.getWaitingSegmentId());
        assertEquals(77, s.getPathStartPointId());
    }

    @Test
    @DisplayName("QrNavigationTask — getSegmentOffset and toString")
    void testQrNavTaskOffsetAndToString() {
        QrNavigationTask task = QrNavigationTask.builder()
                .totalSegmentCount(10).segmentOffset(5)
                .addSegment(new QrNavigationTask.Segment.Builder(1, 10).build())
                .build();
        assertEquals(5, task.getSegmentOffset());
        assertTrue(task.toString().contains("10"));
        assertTrue(task.toString().contains("5"));
    }

    @Test
    @DisplayName("KecongMessageDecoder and KecongMessageEncoder — instantiate")
    void testInstantiateDecoderEncoder() {
        assertNotNull(new KecongMessageDecoder());
        assertNotNull(new KecongMessageEncoder());
    }

    @Test
    @DisplayName("KecongProtocolFrame — getMessageType")
    void testProtocolFrameGetMessageType() {
        byte[] raw = new byte[28 + 4];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[16]);
        buf.put((byte) 1); buf.put((byte) 0);
        buf.putShort((short) 0);
        buf.put((byte) 0x10); buf.put((byte) 0);
        buf.put((byte) 0); buf.put((byte) 0);
        buf.putShort((short) 4);
        buf.putShort((short) 0);
        buf.put(new byte[4]);

        KecongProtocolFrame frame = KecongProtocolFrame.decode(raw);
        assertNotNull(frame);
        assertEquals(0, frame.getMessageType());
        assertTrue(frame.isRequest());
    }

    @Test
    @DisplayName("NavigationTask — encode with null angle in path splice mode")
    void testEncodeNavTaskNullAnglePathSplice() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE)
                .addPoint(NavigationTask.TaskPoint.builder().sequenceNumber(0).pointId(5).angle(null).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
    }

    @Test
    @DisplayName("NavigationTask — encode free nav with null angle")
    void testEncodeNavTaskNullAngleFreeMode() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .addPoint(NavigationTask.TaskPoint.builder().sequenceNumber(0).pointId(5).angle(null).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
    }

    @Test
    @DisplayName("NavigationTask — encode path with null maxSpeed and null fixedAngle")
    void testEncodePathNullSpeeds() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE)
                .addPoint(NavigationTask.TaskPoint.builder().sequenceNumber(0).pointId(10).build())
                .addPath(NavigationTask.TaskPath.builder().sequenceNumber(1).pathId(50)
                        .travelPose(0).maxSpeed(null).maxAngularSpeed(null)
                        .fixedAngle(null).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
    }

    @Test
    @DisplayName("NavigationTask — encode action with alignment padding")
    void testEncodeActionAlignmentPadding() {
        // Action params length that causes alignment padding
        NavigationTask.TaskAction action = new NavigationTask.TaskAction(
                KecongActionType.ACTION_PALLET_LIFT,
                KecongActionType.CONCURRENT_SINGLE,
                999,
                new byte[]{0x01}); // odd params length, triggers alignment
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE)
                .addPoint(NavigationTask.TaskPoint.builder().sequenceNumber(0).pointId(5)
                        .addAction(action).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
    }

    @Test
    @DisplayName("TrafficResource — null paths constructor (via fromQueryResponse mismatch)")
    void testTrafficResourceFromQueryMismatch() {
        // pathCount=2 but no remaining bytes → triggers break
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);  // hasRequest
        buf.put((byte) 2);  // pathCount=2
        buf.putShort((short) 0);
        TrafficResource res = TrafficResource.fromQueryResponse(buf.array());
        assertNotNull(res);
        assertTrue(res.hasRequest());
        assertEquals(0, res.getPathCount()); // break triggered, no paths added
    }

    @Test
    @DisplayName("QrNavigationTask — encodeSegments with odd number of params")
    void testQrEncodeSegmentsVariousFields() {
        // Test segment with stopRequired=false, laserShieldEnabled=true, rotationLimit=2 (CCW)
        QrNavigationTask.Segment seg = new QrNavigationTask.Segment.Builder(1, 2)
                .dx((short) 100).dy((short) 200)
                .destHeadingAngle(1800)
                .stopRequired(false)
                .laserShieldEnabled(true)
                .linearSpeed(50)
                .rotationLimit(2) // CCW
                .build();
        QrNavigationTask task = QrNavigationTask.builder()
                .addSegment(seg).angleLimitEnabled(false).build();
        byte[] data = task.toF1Bytes();
        assertNotNull(data);
        assertEquals(8 + 16, data.length);
    }

    @Test
    @DisplayName("NavigationTask — free mode specifyAngle=false and angle not null")
    void testFreeNavNoAngleSpecified() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .addPoint(NavigationTask.TaskPoint.builder()
                        .sequenceNumber(0).pointId(10)
                        .angle(1.0f).specifyAngle(false).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
    }

    @Test
    @DisplayName("NavigationTask — free mode specifyAngle=true and angle is null")
    void testFreeNavSpecifyAngleNullAngle() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .addPoint(NavigationTask.TaskPoint.builder()
                        .sequenceNumber(0).pointId(10)
                        .angle(null).specifyAngle(true).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
    }

    @Test
    @DisplayName("NavigationTask — free mode without actions")
    void testFreeNavNoActions() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_FREE)
                .addPoint(NavigationTask.TaskPoint.builder()
                        .sequenceNumber(0).pointId(10).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
    }

    @Test
    @DisplayName("NavigationTask — path splice with specifyAngle=true and angle set")
    void testPathSpliceSpecifyAngle() {
        NavigationTask task = NavigationTask.builder()
                .orderId(1).taskKey(1)
                .navigationMode(NavigationTask.NAV_MODE_PATH_SPLICE)
                .addPoint(NavigationTask.TaskPoint.builder()
                        .sequenceNumber(0).pointId(10)
                        .angle(1.57f).specifyAngle(true).build())
                .build();
        byte[] data = KecongMessageEncoder.encodeNavigationTask(task);
        assertNotNull(data);
    }

    @Test
    @DisplayName("TrafficResource — null paths defensive (via reflection)")
    void testTrafficResourceNullDefensive() throws Exception {
        // Hit the paths != null ? ... : Collections.emptyList() null branch
        java.lang.reflect.Constructor<TrafficResource> ctor =
                TrafficResource.class.getDeclaredConstructor(boolean.class, boolean.class, java.util.List.class);
        ctor.setAccessible(true);
        TrafficResource res = ctor.newInstance(false, false, null);
        assertNotNull(res);
        assertFalse(res.hasRequest());
        assertFalse(res.isSuccess());
        assertEquals(0, res.getPathCount());
    }
}
