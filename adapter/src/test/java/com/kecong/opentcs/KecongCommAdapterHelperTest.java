package com.kecong.opentcs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for KecongCommAdapter non-UDP helper methods.
 * These are stateless utility methods that can be tested independently.
 */
@DisplayName("KecongCommAdapter Helpers")
class KecongCommAdapterHelperTest {

    @Test
    @DisplayName("Factory implements Serializable")
    void testFactoryIsSerializable() {
        assertTrue(new KecongCommAdapterFactory() instanceof java.io.Serializable);
    }

    @Test
    @DisplayName("Process model stores vehicle reference")
    void testProcessModelVehicleRef() {
        org.opentcs.data.model.Vehicle v = new org.opentcs.data.model.Vehicle("TestV");
        KecongVehicleProcessModel model = new KecongVehicleProcessModel(v);
        assertEquals("TestV", model.getName());
    }

    @Test
    @DisplayName("Process model KecongProperties initial state")
    void testProcessModelKecongProps() {
        org.opentcs.data.model.Vehicle v = new org.opentcs.data.model.Vehicle("TestV");
        KecongVehicleProcessModel model = new KecongVehicleProcessModel(v);
        assertEquals(0, model.getLocalizationStatus());
        assertEquals(0, model.getConfidence());
        assertEquals(0, model.getKecongWorkMode());
        assertEquals(0, model.getKecongAgvState());
        assertEquals(0f, model.getBatteryPercent(), 0.001f);
        assertEquals(0, model.getChargeStatus());
        assertEquals("", model.getErrorCodes());
        assertEquals(0, model.getCmdSequence());
        assertFalse(model.isAutoReady());
    }

    @Test
    @DisplayName("Process model all KecongProps round-trip")
    void testProcessModelPropsRoundTrip() {
        org.opentcs.data.model.Vehicle v = new org.opentcs.data.model.Vehicle("TestV");
        KecongVehicleProcessModel model = new KecongVehicleProcessModel(v);

        model.setLocalizationStatus(3);
        model.setConfidence(95);
        model.setKecongWorkMode(3);
        model.setKecongAgvState(1);
        model.setBatteryPercent(0.88f);
        model.setChargeStatus(1);
        model.setErrorCodes("0x2004");
        model.setCmdSequence(100);
        model.setAutoReady(true);

        assertEquals(3, model.getLocalizationStatus());
        assertEquals(95, model.getConfidence());
        assertEquals(3, model.getKecongWorkMode());
        assertEquals(1, model.getKecongAgvState());
        assertEquals(0.88f, model.getBatteryPercent(), 0.001f);
        assertEquals(1, model.getChargeStatus());
        assertEquals("0x2004", model.getErrorCodes());
        assertEquals(100, model.getCmdSequence());
        assertTrue(model.isAutoReady());
    }

    @Test
    @DisplayName("Factory description is consistent")
    void testFactoryDescriptionConsistent() {
        KecongCommAdapterFactory f1 = new KecongCommAdapterFactory();
        KecongCommAdapterFactory f2 = new KecongCommAdapterFactory();
        assertEquals(f1.getDescription().getDescription(), f2.getDescription().getDescription());
    }
}
