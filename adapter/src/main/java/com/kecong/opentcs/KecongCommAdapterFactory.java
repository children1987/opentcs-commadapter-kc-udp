package com.kecong.opentcs;

import org.opentcs.data.model.Vehicle;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapterFactory;
import org.opentcs.drivers.vehicle.VehicleCommAdapterDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for creating KecongCommAdapter instances for openTCS vehicles.
 *
 * <p>Vehicle properties used for configuration:
 * <ul>
 *   <li>{@code kecong:navHost} — Laser/hybrid navigation controller IP (default: 192.168.100.178)</li>
 *   <li>{@code kecong:navPort} — Laser/hybrid navigation UDP port (default: 17804)</li>
 *   <li>{@code kecong:qrHost} — QR/magnetic navigation controller IP (default: 192.168.100.200)</li>
 *   <li>{@code kecong:qrPort} — QR/magnetic navigation UDP port (default: 17800)</li>
 *   <li>{@code kecong:authCode} — Protocol auth code (optional, default: built-in Kecong standard auth code)</li>
 *   <li>{@code kecong:pollInterval} — Status polling interval in ms (default: 100)</li>
 *   <li>{@code kecong:energySource} — Battery energy source: PROTOCOL | READ_VAR | READ_MULTI_VAR (default: PROTOCOL)</li>
 *   <li>{@code kecong:energyVarName} — Variable name for READ_VAR / READ_MULTI_VAR mode (e.g. "battery_percent")</li>
 *   <li>{@code kecong:energyVarOffset} — Byte offset for READ_MULTI_VAR mode (default: 0)</li>
 *   <li>{@code kecong:energyVarPort} — Channel for var read: NAV | QR (default: NAV)</li>
 * </ul>
 *
 * <p>To use with openTCS, register this factory in the Kernel configuration:
 * <pre>
 * {@code <adapter factoryClass="com.kecong.opentcs.KecongCommAdapterFactory"/>}
 * </pre>
 */
public class KecongCommAdapterFactory implements VehicleCommAdapterFactory, Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(KecongCommAdapterFactory.class);

    /** Property key prefix for Kecong driver configuration */
    private static final String PROP_PREFIX = "kecong:";

    private boolean initialized;

    @Override
    public void initialize() {
        if (initialized) return;
        LOG.info("Initializing KecongCommAdapterFactory...");
        initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void terminate() {
        if (!initialized) return;
        LOG.info("Terminating KecongCommAdapterFactory...");
        initialized = false;
    }

    @Nullable
    @Override
    public VehicleCommAdapter getAdapterFor(@Nonnull Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");

        LOG.info("Creating KecongCommAdapter for vehicle '{}'", vehicle.getName());

        KecongVehicleProcessModel processModel = new KecongVehicleProcessModel(vehicle);

        // Pass kernel vehicle service for position resolution
        var svc = KecongAdapterModule.getVehicleService();
        if (svc != null) {
            processModel.setVehicleService(svc);
            LOG.info("Vehicle service injected for position resolution");
        }

        String navHost = getProperty(vehicle, "navHost", "192.168.100.178");
        int navPort = Integer.parseInt(getProperty(vehicle, "navPort", "17804"));
        int qrPort = Integer.parseInt(getProperty(vehicle, "qrPort", "17800"));
        String qrHost = getProperty(vehicle, "qrHost", "192.168.100.200");
        String authCode = getProperty(vehicle, "authCode", null);
        int pollInterval = Integer.parseInt(getProperty(vehicle, "pollInterval", "100"));
        boolean autoInit = Boolean.parseBoolean(getProperty(vehicle, "autoInit", "false"));

        // Build energy config from vehicle properties
        KecongEnergyConfig energyConfig = KecongEnergyConfig.fromVehicleProperties(vehicle.getProperties());

        if (authCode == null || authCode.isEmpty()) {
            LOG.info("No authCode configured for vehicle '{}', using default auth code", vehicle.getName());
        }

        if (autoInit) {
            LOG.info("Auto-initialization enabled for vehicle '{}'", vehicle.getName());
        }

        LOG.info("Energy source for '{}': {} (varName={}, varPort={})",
                vehicle.getName(), energyConfig.getSource(),
                energyConfig.getVarName(), energyConfig.getVarPort());

        return new KecongCommAdapter(processModel, navHost, navPort, qrPort, qrHost, authCode, pollInterval, autoInit, energyConfig);
    }

    @Override
    public VehicleCommAdapterDescription getDescription() {
        return new SerializableDescription();
    }

    private static class SerializableDescription extends VehicleCommAdapterDescription {
        @Override
        public String getDescription() {
            return "openTCS driver for Kecong (科聪) MRC/FRC series AGV controllers via UDP protocol V2.0";
        }

        @Override
        public boolean isSimVehicleCommAdapter() { return false; }
    }

    @Override
    public boolean providesAdapterFor(@Nonnull Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");
        // Check if this vehicle has Kecong properties configured
        Map<String, String> props = vehicle.getProperties();
        return props.containsKey(PROP_PREFIX + "authCode")
                || props.containsKey(PROP_PREFIX + "navHost")
                || props.containsKey(PROP_PREFIX + "qrHost");
    }

    /**
     * Get a property value from vehicle properties.
     * Checks vehicle properties, then system properties.
     */
    private String getProperty(Vehicle vehicle, String key, String defaultValue) {
        String fullKey = PROP_PREFIX + key;
        Map<String, String> props = vehicle.getProperties();
        if (props.containsKey(fullKey)) {
            return props.get(fullKey);
        }
        return System.getProperty(fullKey, defaultValue);
    }
}
