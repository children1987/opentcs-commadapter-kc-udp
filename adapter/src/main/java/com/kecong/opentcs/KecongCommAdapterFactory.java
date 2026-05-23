package com.kecong.opentcs;

import org.opentcs.data.model.Vehicle;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Factory for creating KecongCommAdapter instances for openTCS vehicles.
 *
 * <p>Vehicle properties used for configuration:
 * <ul>
 *   <li>{@code kecong:host} — Controller IP address (default: 192.168.100.178)</li>
 *   <li>{@code kecong:port} — Controller UDP port (default: 17804)</li>
 *   <li>{@code kecong:authCode} — Protocol auth code (required)</li>
 *   <li>{@code kecong:pollInterval} — Status polling interval in ms (default: 100)</li>
 * </ul>
 *
 * <p>To use with openTCS, register this factory in the Kernel configuration:
 * <pre>
 * {@code <adapter factoryClass="com.kecong.opentcs.KecongCommAdapterFactory"/>}
 * </pre>
 */
public class KecongCommAdapterFactory implements VehicleCommAdapterFactory {

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

    @Nonnull
    @Override
    public VehicleCommAdapter getAdapterFor(@Nonnull Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");

        LOG.info("Creating KecongCommAdapter for vehicle '{}'", vehicle.getName());

        KecongVehicleProcessModel processModel = new KecongVehicleProcessModel(vehicle);

        String host = getProperty(vehicle, "host", "192.168.100.178");
        int port = Integer.parseInt(getProperty(vehicle, "port", "17804"));
        String authCode = getProperty(vehicle, "authCode", "");
        int pollInterval = Integer.parseInt(getProperty(vehicle, "pollInterval", "100"));

        if (authCode.isEmpty()) {
            LOG.warn("No authCode configured for vehicle '{}', driver may not work", vehicle.getName());
        }

        return new KecongCommAdapter(processModel, host, port, authCode, pollInterval);
    }

    @Nonnull
    @Override
    public AdapterComponents getAdapterComponents() {
        return new AdapterComponents()
                .withProcessModelClass(KecongVehicleProcessModel.class)
                .withAdapterClass(KecongCommAdapter.class);
    }

    @Override
    public boolean providesAdapterFor(@Nonnull Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");
        // Check if this vehicle has Kecong properties configured
        Map<String, String> props = vehicle.getProperties();
        return props.containsKey(PROP_PREFIX + "authCode")
                || props.containsKey(PROP_PREFIX + "host");
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
