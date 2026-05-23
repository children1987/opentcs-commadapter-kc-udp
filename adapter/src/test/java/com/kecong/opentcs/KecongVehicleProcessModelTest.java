package com.kecong.opentcs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Triple;

/**
 * Tests for KecongVehicleProcessModel.
 */
@DisplayName("KecongVehicleProcessModel")
class KecongVehicleProcessModelTest {

    private Vehicle testVehicle;
    private KecongVehicleProcessModel model;

    @BeforeEach
    void setUp() {
        testVehicle = new Vehicle("TestVehicle-1");
        model = new KecongVehicleProcessModel(testVehicle);
    }

    @Test
    @DisplayName("Vehicle name is set from constructor")
    void testVehicleName() {
        assertEquals("TestVehicle-1", model.getVehicleName());
    }

    @Test
    @DisplayName("Default auto-ready is false")
    void testDefaultAutoReady() {
        assertFalse(model.isAutoReady());
    }

    @Test
    @DisplayName("Set and get auto-ready")
    void testAutoReady() {
        model.setAutoReady(true);
        assertTrue(model.isAutoReady());

        model.setAutoReady(false);
        assertFalse(model.isAutoReady());
    }

    @Test
    @DisplayName("Localization status getter/setter")
    void testLocalizationStatus() {
        assertEquals(0, model.getLocalizationStatus());
        model.setLocalizationStatus(3);
        assertEquals(3, model.getLocalizationStatus());
    }

    @Test
    @DisplayName("Confidence getter/setter")
    void testConfidence() {
        assertEquals(0, model.getConfidence());
        model.setConfidence(85);
        assertEquals(85, model.getConfidence());
    }

    @Test
    @DisplayName("Work mode getter/setter")
    void testWorkMode() {
        assertEquals(0, model.getKecongWorkMode());
        model.setKecongWorkMode(3);
        assertEquals(3, model.getKecongWorkMode());
    }

    @Test
    @DisplayName("AGV state getter/setter")
    void testAgvState() {
        assertEquals(0, model.getKecongAgvState());
        model.setKecongAgvState(1);
        assertEquals(1, model.getKecongAgvState());
    }

    @Test
    @DisplayName("Battery percent getter/setter")
    void testBatteryPercent() {
        assertEquals(0f, model.getBatteryPercent(), 0.001f);
        model.setBatteryPercent(0.75f);
        assertEquals(0.75f, model.getBatteryPercent(), 0.001f);
    }

    @Test
    @DisplayName("Charge status getter/setter")
    void testChargeStatus() {
        assertEquals(0, model.getChargeStatus());
        model.setChargeStatus(1);
        assertEquals(1, model.getChargeStatus());
    }

    @Test
    @DisplayName("Error codes getter/setter")
    void testErrorCodes() {
        assertEquals("", model.getErrorCodes());
        model.setErrorCodes("0x0108,0x0113");
        assertEquals("0x0108,0x0113", model.getErrorCodes());
    }

    @Test
    @DisplayName("Command sequence getter/setter")
    void testCmdSequence() {
        assertEquals(0, model.getCmdSequence());
        model.setCmdSequence(42);
        assertEquals(42, model.getCmdSequence());
    }

    @Test
    @DisplayName("Set vehicle state updates process model")
    void testSetVehicleState() {
        model.setVehicleState(Vehicle.State.IDLE);
        assertEquals(Vehicle.State.IDLE, model.getVehicleState());

        model.setVehicleState(Vehicle.State.EXECUTING);
        assertEquals(Vehicle.State.EXECUTING, model.getVehicleState());

        model.setVehicleState(Vehicle.State.ERROR);
        assertEquals(Vehicle.State.ERROR, model.getVehicleState());
    }

    @Test
    @DisplayName("Set vehicle position")
    void testSetVehiclePosition() {
        Triple pos = new Triple(1000, 2000, 0);
        model.setVehiclePosition(pos);
        assertEquals(pos, model.getVehiclePosition());
    }

    @Test
    @DisplayName("Set vehicle energy level")
    void testSetVehicleEnergyLevel() {
        model.setVehicleEnergyLevel(85);
        assertEquals(85, model.getVehicleEnergyLevel());
    }

    @Test
    @DisplayName("Default property values for unset attributes")
    void testDefaultPropertyValues() {
        // When no property set, getters should return defaults
        assertEquals(0, model.getConfidence());
        assertEquals(0, model.getKecongWorkMode());
        assertEquals(0, model.getKecongAgvState());
        assertEquals(0f, model.getBatteryPercent(), 0.001f);
        assertEquals(0, model.getChargeStatus());
        assertEquals("", model.getErrorCodes());
        assertEquals(0, model.getCmdSequence());
    }
}
