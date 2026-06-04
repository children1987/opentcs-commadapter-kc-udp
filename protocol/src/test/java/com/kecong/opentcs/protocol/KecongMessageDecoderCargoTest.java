package com.kecong.opentcs.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for decodeCargoStatus (0xB0 response).
 */
@DisplayName("decodeCargoStatus")
class KecongMessageDecoderCargoTest {

    @Test
    @DisplayName("Decodes loaded (0x01)")
    void testLoaded() {
        assertTrue(KecongMessageDecoder.decodeCargoStatus(new byte[]{0x01}));
    }

    @Test
    @DisplayName("Decodes unloaded (0x00)")
    void testUnloaded() {
        assertFalse(KecongMessageDecoder.decodeCargoStatus(new byte[]{0x00}));
    }

    @Test
    @DisplayName("Returns false for null input")
    void testNull() {
        assertFalse(KecongMessageDecoder.decodeCargoStatus(null));
    }

    @Test
    @DisplayName("Returns false for empty input")
    void testEmpty() {
        assertFalse(KecongMessageDecoder.decodeCargoStatus(new byte[0]));
    }

    @Test
    @DisplayName("Returns false for non-0x01 values")
    void testOtherValues() {
        assertFalse(KecongMessageDecoder.decodeCargoStatus(new byte[]{0x02}));
        assertFalse(KecongMessageDecoder.decodeCargoStatus(new byte[]{(byte) 0xFF}));
    }
}
