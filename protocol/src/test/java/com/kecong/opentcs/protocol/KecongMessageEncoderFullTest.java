package com.kecong.opentcs.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Additional tests for KecongMessageEncoder to fill coverage gaps.
 */
@DisplayName("KecongMessageEncoder gap coverage")
class KecongMessageEncoderFullTest {

    @Test
    @DisplayName("encodeEmptyRequest returns empty byte array")
    void testEmptyRequest() {
        byte[] data = KecongMessageEncoder.encodeEmptyRequest();
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeImmediateAction returns non-null non-empty")
    void testImmediateAction() {
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                KecongActionType.ACTION_PAUSE,
                KecongActionType.CONCURRENT_SINGLE,
                1, new byte[]{0x01});
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("encodeImmediateAction with null params")
    void testImmediateActionNullParams() {
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                KecongActionType.ACTION_RESUME,
                KecongActionType.CONCURRENT_ALL,
                2, null);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("encodeImmediateAction with empty params")
    void testImmediateActionEmptyParams() {
        byte[] data = KecongMessageEncoder.encodeImmediateAction(
                KecongActionType.ACTION_CANCEL,
                KecongActionType.CONCURRENT_ACTION_ONLY,
                3, new byte[0]);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }

    @Test
    @DisplayName("encodeQrNavTask returns non-null for null input")
    void testQrNavTaskNull() {
        byte[] data = KecongMessageEncoder.encodeQrNavTask(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeQrLongPathTask returns non-null for null input")
    void testQrLongPathTaskNull() {
        byte[] data = KecongMessageEncoder.encodeQrLongPathTask(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeMagneticTask returns non-null for null input")
    void testMagneticTaskNull() {
        byte[] data = KecongMessageEncoder.encodeMagneticTask(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeMagneticControl returns non-null for null input")
    void testMagneticControlNull() {
        byte[] data = KecongMessageEncoder.encodeMagneticControl(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeMagneticRelocalize returns non-null for null input")
    void testMagneticRelocalizeNull() {
        byte[] data = KecongMessageEncoder.encodeMagneticRelocalize(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }

    @Test
    @DisplayName("encodeTrafficResult returns non-null for null input")
    void testTrafficResultNull() {
        byte[] data = KecongMessageEncoder.encodeTrafficResult(null);
        assertNotNull(data);
        assertEquals(0, data.length);
    }
}
