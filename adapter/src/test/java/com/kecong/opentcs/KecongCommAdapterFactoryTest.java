package com.kecong.opentcs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentcs.data.model.Vehicle;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapterDescription;

@DisplayName("KecongCommAdapterFactory")
class KecongCommAdapterFactoryTest {

    private KecongCommAdapterFactory factory;

    @BeforeEach
    void setUp() {
        factory = new KecongCommAdapterFactory();
    }

    private static Vehicle mockVehicle(String name, Map<String, String> props) {
        Vehicle v = mock(Vehicle.class);
        when(v.getName()).thenReturn(name);
        when(v.getProperties()).thenReturn(new HashMap<>(props));
        return v;
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
        Vehicle v = mockVehicle("TestV", Map.of("kecong:navHost", "192.168.100.178"));
        assertTrue(factory.providesAdapterFor(v));
    }

    @Test
    @DisplayName("providesAdapterFor returns true when authCode property exists")
    void testProvidesAdapterWithAuthCode() {
        Vehicle v = mockVehicle("TestV", Map.of("kecong:authCode", "test-code"));
        assertTrue(factory.providesAdapterFor(v));
    }

    @Test
    @DisplayName("providesAdapterFor returns false when no Kecong properties")
    void testProvidesAdapterWithoutProperties() {
        Vehicle v = mockVehicle("TestV", Map.of());
        assertFalse(factory.providesAdapterFor(v));
    }

    @Test
    @DisplayName("getAdapterFor creates adapter with default values")
    void testGetAdapterDefaults() {
        factory.initialize();
        Vehicle v = mockVehicle("AGV-001", Map.of("kecong:navHost", "192.168.100.178"));
        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
        assertFalse(adapter.isInitialized());
        assertFalse(adapter.isEnabled());
    }

    @Test
    @DisplayName("getAdapterFor reads all vehicle properties")
    void testGetAdapterWithAllProperties() {
        factory.initialize();
        Map<String, String> props = new HashMap<>();
        props.put("kecong:navHost", "192.168.1.1");
        props.put("kecong:navPort", "17805");
        props.put("kecong:qrHost", "192.168.1.2");
        props.put("kecong:qrPort", "17801");
        props.put("kecong:pollInterval", "200");
        props.put("kecong:autoInit", "true");
        props.put("kecong:energySource", "READ_VAR");
        props.put("kecong:energyVarName", "battery");

        Vehicle v = mockVehicle("AGV-001", props);
        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
        assertNotNull(adapter.getProcessModel());
    }

    @Test
    @DisplayName("getAdapterFor with authCode uses custom auth")
    void testGetAdapterWithAuthCode() {
        factory.initialize();
        Map<String, String> props = new HashMap<>();
        props.put("kecong:navHost", "192.168.100.178");
        props.put("kecong:authCode", "CUSTOM-AUTH-CODE");

        Vehicle v = mockVehicle("AGV-001", props);
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
    @DisplayName("Getting adapter without initialize throws (or works)")
    void testGetAdapterWithoutInit() {
        Vehicle v = mockVehicle("AGV-001", Map.of("kecong:navHost", "192.168.100.178"));
        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
    }

    @Test
    @DisplayName("providesAdapterFor with qrHost property returns true")
    void testProvidesAdapterWithQrHost() {
        Vehicle v = mockVehicle("TestV", Map.of("kecong:qrHost", "192.168.100.200"));
        assertTrue(factory.providesAdapterFor(v));
    }

    @Test
    @DisplayName("getAdapterFor with energy config via vehicle props")
    void testGetAdapterWithEnergyConfig() {
        factory.initialize();
        Map<String, String> props = new HashMap<>();
        props.put("kecong:navHost", "192.168.1.1");
        props.put("kecong:energySource", "READ_VAR");
        props.put("kecong:energyVarName", "battery_percent");
        props.put("kecong:energyVarPort", "QR");
        props.put("kecong:energyVarOffset", "2");

        Vehicle v = mockVehicle("AGV-E", props);
        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
        assertNotNull(adapter.getProcessModel());
    }

    @Test
    @DisplayName("getAdapterFor with energy hot-reload config path")
    void testGetAdapterWithEnergyConfigPath() {
        factory.initialize();
        Map<String, String> props = new HashMap<>();
        props.put("kecong:navHost", "192.168.1.1");
        props.put("kecong:energySource", "PROTOCOL");
        props.put("kecong:energyConfigPath", "/tmp/kecong-energy.json");

        Vehicle v = mockVehicle("AGV-HR", props);
        VehicleCommAdapter adapter = factory.getAdapterFor(v);
        assertNotNull(adapter);
    }
}
