package com.kecong.opentcs.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KecongExecutionCode.
 */
@DisplayName("KecongExecutionCode")
class KecongExecutionCodeTest {

    @Test
    @DisplayName("EXEC_SUCCESS returns true for isSuccess")
    void testIsSuccessTrue() {
        assertTrue(KecongExecutionCode.isSuccess(KecongExecutionCode.EXEC_SUCCESS));
    }

    @Test
    @DisplayName("Non-zero codes return false for isSuccess")
    void testIsSuccessFalse() {
        assertFalse(KecongExecutionCode.isSuccess(KecongExecutionCode.EXEC_FAILED_UNKNOWN));
        assertFalse(KecongExecutionCode.isSuccess(KecongExecutionCode.EXEC_AUTH_CODE_ERROR));
        assertFalse(KecongExecutionCode.isSuccess((byte) 0x99));
    }

    @Test
    @DisplayName("describe returns non-null for all defined codes")
    void testDescribeDefined() {
        byte[] codes = {
            KecongExecutionCode.EXEC_SUCCESS,
            KecongExecutionCode.EXEC_FAILED_UNKNOWN,
            KecongExecutionCode.EXEC_SERVICE_CODE_ERROR,
            KecongExecutionCode.EXEC_COMMAND_CODE_ERROR,
            KecongExecutionCode.EXEC_HEADER_ERROR,
            KecongExecutionCode.EXEC_LENGTH_ERROR,
            KecongExecutionCode.EXEC_POINT_COUNT_EXCEEDED,
            KecongExecutionCode.EXEC_SPLICE_OFFSET_MISMATCH,
            KecongExecutionCode.EXEC_SPLICE_SEQ_MISMATCH,
            KecongExecutionCode.EXEC_SPLICE_TASK_SEQ_MISMATCH,
            KecongExecutionCode.EXEC_SPLICE_MAX_EXCEEDED,
            KecongExecutionCode.EXEC_NAV_STATE_CONFLICT,
            KecongExecutionCode.EXEC_AUTH_CODE_ERROR,
        };
        for (byte code : codes) {
            assertNotNull(KecongExecutionCode.describe(code));
            assertFalse(KecongExecutionCode.describe(code).isEmpty());
        }
    }

    @Test
    @DisplayName("describe for unknown code includes hex value")
    void testDescribeUnknown() {
        String desc = KecongExecutionCode.describe((byte) 0x99);
        assertTrue(desc.contains("99") || desc.contains("Unknown"));
    }

    @Test
    @DisplayName("Execution code values are correct")
    void testValues() {
        assertEquals((byte) 0x00, KecongExecutionCode.EXEC_SUCCESS);
        assertEquals((byte) 0x01, KecongExecutionCode.EXEC_FAILED_UNKNOWN);
        assertEquals((byte) 0x02, KecongExecutionCode.EXEC_SERVICE_CODE_ERROR);
        assertEquals((byte) 0x03, KecongExecutionCode.EXEC_COMMAND_CODE_ERROR);
        assertEquals((byte) 0xFF, KecongExecutionCode.EXEC_AUTH_CODE_ERROR);
        assertEquals((byte) 0x80, KecongExecutionCode.EXEC_NAV_STATE_CONFLICT);
    }
}
