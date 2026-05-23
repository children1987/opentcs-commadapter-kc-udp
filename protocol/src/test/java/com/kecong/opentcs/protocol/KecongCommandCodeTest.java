package com.kecong.opentcs.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KecongCommandCode constants.
 */
@DisplayName("KecongCommandCode")
class KecongCommandCodeTest {

    @Test
    @DisplayName("All command codes are unique")
    void testAllUnique() {
        byte[] codes = {
            KecongCommandCode.CMD_WRITE_VAR,
            KecongCommandCode.CMD_READ_VAR,
            KecongCommandCode.CMD_READ_MULTI_VAR,
            KecongCommandCode.CMD_WRITE_MULTI_VAR,
            KecongCommandCode.CMD_AUTO_MANUAL_SWITCH,
            KecongCommandCode.CMD_MANUAL_POSITION,
            KecongCommandCode.CMD_GET_POSITION,
            KecongCommandCode.CMD_NAV_CONTROL,
            KecongCommandCode.CMD_QUERY_RUN_STATUS,
            KecongCommandCode.CMD_QUERY_NAV_STATUS,
            KecongCommandCode.CMD_CONFIRM_POSITION,
            KecongCommandCode.CMD_HYBRID_NAV_TASK,
            KecongCommandCode.CMD_QUERY_ROBOT_STATUS,
            KecongCommandCode.CMD_QUERY_CARGO_STATUS,
            KecongCommandCode.CMD_SUBSCRIPTION,
            KecongCommandCode.CMD_IMMEDIATE_ACTION,
            KecongCommandCode.CMD_SET_CAPABILITY,
            KecongCommandCode.CMD_NEARBY_VEHICLE_INFO,
        };

        for (int i = 0; i < codes.length; i++) {
            for (int j = i + 1; j < codes.length; j++) {
                assertNotEquals(codes[i], codes[j],
                        "Command codes at index " + i + " and " + j + " are not unique");
            }
        }
    }

    @Test
    @DisplayName("Key command code values are correct")
    void testKeyValues() {
        assertEquals((byte) 0xAE, KecongCommandCode.CMD_HYBRID_NAV_TASK);
        assertEquals((byte) 0xAF, KecongCommandCode.CMD_QUERY_ROBOT_STATUS);
        assertEquals((byte) 0xB0, KecongCommandCode.CMD_QUERY_CARGO_STATUS);
        assertEquals((byte) 0xB1, KecongCommandCode.CMD_SUBSCRIPTION);
        assertEquals((byte) 0xB2, KecongCommandCode.CMD_IMMEDIATE_ACTION);
        assertEquals((byte) 0xB7, KecongCommandCode.CMD_SET_CAPABILITY);
        assertEquals((byte) 0xB9, KecongCommandCode.CMD_NEARBY_VEHICLE_INFO);
        assertEquals((byte) 0x00, KecongCommandCode.CMD_WRITE_VAR);
        assertEquals((byte) 0x03, KecongCommandCode.CMD_WRITE_MULTI_VAR);
    }

    @Test
    @DisplayName("Cannot instantiate utility class")
    void testCannotInstantiate() {
        // Verify constructor is private (reflection would fail)
        assertThrows(UnsupportedOperationException.class, () -> {
            var ctor = KecongCommandCode.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            throw new UnsupportedOperationException("Should not reach here");
        });
    }
}
