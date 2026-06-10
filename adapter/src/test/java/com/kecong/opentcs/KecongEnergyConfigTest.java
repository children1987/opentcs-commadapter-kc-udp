package com.kecong.opentcs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("KecongEnergyConfig")
class KecongEnergyConfigTest {

    // ---- fromVehicleProperties ----

    @Test
    @DisplayName("Default source is PROTOCOL when no props")
    void testDefaultSource() {
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(Map.of());
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());
        assertEquals("NAV", c.getVarPort());
        assertEquals(0, c.getVarOffset());
        assertNull(c.getVarName());
        assertNull(c.getConfigFilePath());
    }

    @Test
    @DisplayName("Parse PROTOCOL source explicitly")
    void testParseProtocol() {
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(
                Map.of("kecong:energySource", "PROTOCOL"));
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());
    }

    @Test
    @DisplayName("Parse READ_VAR source")
    void testParseReadVar() {
        Map<String, String> props = new HashMap<>();
        props.put("kecong:energySource", "READ_VAR");
        props.put("kecong:energyVarName", "battery");
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(props);
        assertEquals(KecongEnergyConfig.Source.READ_VAR, c.getSource());
        assertEquals("battery", c.getVarName());
    }

    @Test
    @DisplayName("Parse READ_MULTI_VAR source with offset")
    void testParseReadMultiVar() {
        Map<String, String> props = new HashMap<>();
        props.put("kecong:energySource", "READ_MULTI_VAR");
        props.put("kecong:energyVarName", "B2GW");
        props.put("kecong:energyVarOffset", "24");
        props.put("kecong:energyVarPort", "QR");
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(props);
        assertEquals(KecongEnergyConfig.Source.READ_MULTI_VAR, c.getSource());
        assertEquals("B2GW", c.getVarName());
        assertEquals(24, c.getVarOffset());
        assertEquals("QR", c.getVarPort());
    }

    @Test
    @DisplayName("READ_VAR without varName falls back to PROTOCOL")
    void testReadVarWithoutNameFallsBack() {
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(
                Map.of("kecong:energySource", "READ_VAR"));
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());
    }

    @Test
    @DisplayName("READ_MULTI_VAR without varName falls back to PROTOCOL")
    void testReadMultiVarWithoutNameFallsBack() {
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(
                Map.of("kecong:energySource", "READ_MULTI_VAR"));
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());
    }

    @Test
    @DisplayName("Invalid energySource falls back to PROTOCOL")
    void testInvalidSourceFallsBack() {
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(
                Map.of("kecong:energySource", "INVALID"));
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());
    }

    @Test
    @DisplayName("Parse varPort NAV and QR")
    void testVarPort() {
        KecongEnergyConfig c1 = KecongEnergyConfig.fromVehicleProperties(
                Map.of("kecong:energyVarPort", "NAV"));
        assertEquals("NAV", c1.getVarPort());

        KecongEnergyConfig c2 = KecongEnergyConfig.fromVehicleProperties(
                Map.of("kecong:energyVarPort", "qr"));
        assertEquals("QR", c2.getVarPort());
    }

    @Test
    @DisplayName("Parse energyConfigPath")
    void testConfigPath() {
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(
                Map.of("kecong:energyConfigPath", "/path/to/config.json"));
        assertNotNull(c.getConfigFilePath());
        assertEquals("/path/to/config.json", c.getConfigFilePath().toString().replace('\\', '/'));
    }

    @Test
    @DisplayName("Empty energySource treated as PROTOCOL")
    void testEmptySourceDefaults() {
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(
                Map.of("kecong:energySource", ""));
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());
    }

    @Test
    @DisplayName("PROTOCOL with varName set still works")
    void testProtocolWithVarName() {
        Map<String, String> props = new HashMap<>();
        props.put("kecong:energySource", "PROTOCOL");
        props.put("kecong:energyVarName", "unused_var");
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(props);
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());
        assertEquals("unused_var", c.getVarName()); // stored but ignored
    }

    // ---- JSON hot-reload ----

    @Test
    @DisplayName("reloadFromJsonFile returns false when no config path set")
    void testReloadNoPath() {
        KecongEnergyConfig c = KecongEnergyConfig.fromVehicleProperties(Map.of());
        assertFalse(c.reloadFromJsonFile());
    }

    @Test
    @DisplayName("reloadFromJsonFile returns false for non-existent file")
    void testReloadFileNotFound() {
        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setConfigFilePath(Path.of("/nonexistent/path/config.json"));
        assertFalse(c.reloadFromJsonFile());
    }

    @Test
    @DisplayName("reloadFromJsonFile parses valid JSON")
    void testReloadValidJson(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("energy-config.json");
        String json = "{\"energySource\":\"READ_VAR\",\"energyVarName\":\"my_battery\",\"energyVarOffset\":4,\"energyVarPort\":\"QR\"}";
        Files.write(jsonFile, json.getBytes(StandardCharsets.UTF_8));

        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setConfigFilePath(jsonFile);
        // Defaults
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());

        boolean changed = c.reloadFromJsonFile();
        assertTrue(changed);
        assertEquals(KecongEnergyConfig.Source.READ_VAR, c.getSource());
        assertEquals("my_battery", c.getVarName());
        assertEquals(4, c.getVarOffset());
        assertEquals("QR", c.getVarPort());
    }

    @Test
    @DisplayName("reloadFromJsonFile returns false if unchanged (same mtime)")
    void testReloadSameMtime(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("energy-config.json");
        Files.write(jsonFile, "{\"energySource\": \"READ_VAR\"}".getBytes(StandardCharsets.UTF_8));

        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setConfigFilePath(jsonFile);

        assertTrue(c.reloadFromJsonFile());  // first load
        assertFalse(c.reloadFromJsonFile()); // same mtime, no reload
    }

    @Test
    @DisplayName("reloadFromJsonFile with partial JSON only overrides specified fields")
    void testReloadPartialJson(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("energy-config.json");

        // Set initial values
        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        c.setVarName("B2GW");
        c.setVarOffset(24);
        c.setVarPort("NAV");
        c.setConfigFilePath(jsonFile);

        // Override only source and varName
        Files.write(jsonFile, "{\"energySource\":\"PROTOCOL\",\"energyVarName\":\"new_var\"}".getBytes(StandardCharsets.UTF_8));

        assertTrue(c.reloadFromJsonFile());
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource());
        assertEquals("new_var", c.getVarName());
        assertEquals(24, c.getVarOffset());   // unchanged
        assertEquals("NAV", c.getVarPort());  // unchanged
    }

    @Test
    @DisplayName("reloadFromJsonFile with invalid source keeps old value")
    void testReloadInvalidSource(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("energy-config.json");
        Files.write(jsonFile, "{\"energySource\": \"BAD\"}".getBytes(StandardCharsets.UTF_8));

        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setConfigFilePath(jsonFile);
        c.setSource(KecongEnergyConfig.Source.READ_VAR);

        c.reloadFromJsonFile();
        // Invalid source in JSON should be ignored, old value preserved
        assertEquals(KecongEnergyConfig.Source.READ_VAR, c.getSource());
    }

    @Test
    @DisplayName("reloadFromJsonFile with empty JSON does not crash")
    void testReloadEmptyJson(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("energy-config.json");
        Files.write(jsonFile, "{}".getBytes(StandardCharsets.UTF_8));

        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setConfigFilePath(jsonFile);

        assertTrue(c.reloadFromJsonFile());
        assertEquals(KecongEnergyConfig.Source.PROTOCOL, c.getSource()); // unchanged
    }

    @Test
    @DisplayName("reloadFromJsonFile with negative varOffset")
    void testReloadNegativeOffset(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("energy-config.json");
        Files.write(jsonFile, "{\"energyVarOffset\": -1}".getBytes(StandardCharsets.UTF_8));

        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setConfigFilePath(jsonFile);

        assertTrue(c.reloadFromJsonFile());
        assertEquals(-1, c.getVarOffset());
    }

    // ---- setters/getters ----

    @Test
    @DisplayName("setters and getters round-trip")
    void testSettersGetters() {
        KecongEnergyConfig c = new KecongEnergyConfig();

        c.setSource(KecongEnergyConfig.Source.READ_VAR);
        assertEquals(KecongEnergyConfig.Source.READ_VAR, c.getSource());

        c.setVarName("test");
        assertEquals("test", c.getVarName());

        c.setVarOffset(8);
        assertEquals(8, c.getVarOffset());

        c.setVarPort("QR");
        assertEquals("QR", c.getVarPort());

        c.setConfigFilePath(Path.of("/tmp/cfg.json"));
        assertEquals(Path.of("/tmp/cfg.json"), c.getConfigFilePath());
    }

    @Test
    @DisplayName("varPort returns NAV when set to null")
    void testVarPortNullDefaults() {
        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setVarPort(null);
        assertEquals("NAV", c.getVarPort());
    }

    @Test
    @DisplayName("varPort defaults to NAV for new instance")
    void testVarPortDefault() {
        KecongEnergyConfig c = new KecongEnergyConfig();
        assertEquals("NAV", c.getVarPort());
    }

    @Test
    @DisplayName("Source enum has all three values")
    void testSourceEnumValues() {
        assertEquals(3, KecongEnergyConfig.Source.values().length);
        assertNotNull(KecongEnergyConfig.Source.valueOf("PROTOCOL"));
        assertNotNull(KecongEnergyConfig.Source.valueOf("READ_VAR"));
        assertNotNull(KecongEnergyConfig.Source.valueOf("READ_MULTI_VAR"));
    }

    @Test @DisplayName("reloadFromJsonFile IOException returns false")
    void testReloadIoError(@TempDir Path tmp) {
        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setConfigFilePath(tmp); // directory not file → IOException on read
        assertFalse(c.reloadFromJsonFile());
    }

    @Test @DisplayName("all setter paths full coverage")
    void testAllSettersFull() {
        KecongEnergyConfig c = new KecongEnergyConfig();
        c.setSource(KecongEnergyConfig.Source.READ_MULTI_VAR);
        c.setVarName("x"); c.setVarOffset(10); c.setVarPort("NAV");
        assertEquals(KecongEnergyConfig.Source.READ_MULTI_VAR, c.getSource());
        assertEquals("x", c.getVarName()); assertEquals(10, c.getVarOffset()); assertEquals("NAV", c.getVarPort());
    }
}
