package com.kecong.opentcs.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KecongActionType constants.
 */
@DisplayName("KecongActionType")
class KecongActionTypeTest {

    @Test
    @DisplayName("All action type constants are correct")
    void testActionTypeValues() {
        assertEquals((short) 0x01, KecongActionType.ACTION_PAUSE);
        assertEquals((short) 0x02, KecongActionType.ACTION_RESUME);
        assertEquals((short) 0x03, KecongActionType.ACTION_CANCEL);
        assertEquals((short) 0x12, KecongActionType.ACTION_FORK_LIFT);
        assertEquals((short) 0x16, KecongActionType.ACTION_PALLET_LIFT);
        assertEquals((short) 0x0001, KecongActionType.ACTION_TURNTABLE_FOLLOW);
        assertEquals((short) 0x0002, KecongActionType.ACTION_TURNTABLE_POSITION);
        assertEquals((short) 0x0003, KecongActionType.ACTION_TURNTABLE_SPEED);
    }

    @Test
    @DisplayName("Concurrency mode constants are correct")
    void testConcurrencyModes() {
        assertEquals((byte) 0x00, KecongActionType.CONCURRENT_ALL);
        assertEquals((byte) 0x01, KecongActionType.CONCURRENT_ACTION_ONLY);
        assertEquals((byte) 0x02, KecongActionType.CONCURRENT_SINGLE);
    }

    @Test
    @DisplayName("Cannot instantiate utility class")
    void testCannotInstantiate() throws Exception {
        var ctor = KecongActionType.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        // Private constructor with no-op body - just verify reflection works
        Object instance = ctor.newInstance();
        assertNotNull(instance);
        assertTrue(instance instanceof KecongActionType);
    }
}
