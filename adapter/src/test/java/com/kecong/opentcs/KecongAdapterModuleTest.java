package com.kecong.opentcs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for KecongAdapterModule.
 */
@DisplayName("KecongAdapterModule")
class KecongAdapterModuleTest {

    @Test
    @DisplayName("Module can be instantiated")
    void testInstantiation() {
        KecongAdapterModule module = new KecongAdapterModule();
        assertNotNull(module);
    }

    @Test
    @DisplayName("Module extends KernelInjectionModule")
    void testIsKernelInjectionModule() {
        KecongAdapterModule module = new KecongAdapterModule();
        assertTrue(module instanceof org.opentcs.customizations.kernel.KernelInjectionModule);
    }
}
