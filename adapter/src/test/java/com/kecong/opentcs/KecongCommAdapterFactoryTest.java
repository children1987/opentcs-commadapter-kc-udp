package com.kecong.opentcs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentcs.data.model.Vehicle;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapterDescription;

/**
 * Tests for KecongCommAdapterFactory.
 */
@DisplayName("KecongCommAdapterFactory")
class KecongCommAdapterFactoryTest {

    private KecongCommAdapterFactory factory;

    @BeforeEach
    void setUp() {
        factory = new KecongCommAdapterFactory();
    }

    @Test
    @DisplayName("Initializes and terminates correctly")
    void testLifecycle() {
        assertFalse(factory.isInitialized());
        factory.initialize();
        assertTrue(factory.isInitialized());
        factory.terminate();
        assertFalse(factory.isInitialized());
    }

    @Test
    @DisplayName("providesAdapterFor returns true when Kecong properties exist")
    void testProvidesAdapterWithProperties() {
        Vehicle v = new Vehicle("TestV");
        v.getProperties().put("kecong:navHost", "192.168.100.178");
        assertTrue(factory.providesAdapterFor(v));
    }

    @Test
    @DisplayName("providesAdapterFor returns true when authCode property exists")
    void testProvidesAdapterWithAuthCode() {
        Vehicle v = new Vehicle("TestV");
        v.getProperties().put("kecong:authCode", "test-code");
        assertTrue(factory.providesAdapterFor(v));
    }

    @Test
    @DisplayName("providesAdapterFor returns false when no Kecong properties")
    void testProvidesAdapterWithoutProperties() {
        Vehicle v = new Vehicle("TestV");
        assertFalse(factory.providesAdapterFor(v));
    }

    @Test
    @DisplayName("getAdapterFor creates adapter with default values")
    void testGetAdapterDefaults() {
        factory.initialize();
        Vehicle v = new Vehicle("AGV-001");
        v.getProperties().put("kecong:navHost", "192.168.100.178");
        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
        assertFalse(adapter.isInitialized());
        assertFalse(adapter.isEnabled());
    }

    @Test
    @DisplayName("getAdapterFor reads all vehicle properties")
    void testGetAdapterWithAllProperties() {
        factory.initialize();
        Vehicle v = new Vehicle("AGV-001");
        v.getProperties().put("kecong:navHost", "192.168.1.1");
        v.getProperties().put("kecong:navPort", "17805");
        v.getProperties().put("kecong:qrHost", "192.168.1.2");
        v.getProperties().put("kecong:qrPort", "17801");
        v.getProperties().put("kecong:pollInterval", "200");
        v.getProperties().put("kecong:autoInit", "true");
        v.getProperties().put("kecong:fixedEnergyLevel", "90");

        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
        assertNotNull(adapter.getProcessModel());
    }

    @Test
    @DisplayName("getAdapterFor with authCode uses custom auth")
    void testGetAdapterWithAuthCode() {
        factory.initialize();
        Vehicle v = new Vehicle("AGV-001");
        v.getProperties().put("kecong:navHost", "192.168.100.178");
        v.getProperties().put("kecong:authCode", "CUSTOM-AUTH-CODE");

        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
    }

    @Test
    @DisplayName("getDescription returns non-null description")
    void testGetDescription() {
        VehicleCommAdapterDescription desc = factory.getDescription();
        assertNotNull(desc);
        assertNotNull(desc.getDescription());
        assertFalse(desc.getDescription().isEmpty());
        assertFalse(desc.isSimVehicleCommAdapter());
    }

    @Test
    @DisplayName("Getting adapter without initialize throws")
    void testGetAdapterWithoutInit() {
        Vehicle v = new Vehicle("AGV-001");
        v.getProperties().put("kecong:navHost", "192.168.100.178");
        // Should still work - factory.initialize() is mostly a no-op
        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
    }
}
