package com.kecong.opentcs;

import com.google.inject.AbstractModule;
import org.opentcs.drivers.vehicle.management.VehicleCommAdapterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guice module that registers the KecongCommAdapterFactory with the openTCS kernel.
 */
public class KecongAdapterModule extends AbstractModule {

    private static final Logger LOG = LoggerFactory.getLogger(KecongAdapterModule.class);

    @Override
    protected void configure() {
        LOG.info("Registering KecongCommAdapterFactory via Guice module");
        bind(VehicleCommAdapterFactory.class).to(KecongCommAdapterFactory.class);
    }
}
