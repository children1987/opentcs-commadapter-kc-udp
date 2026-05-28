package com.kecong.opentcs;

import org.opentcs.customizations.kernel.KernelInjectionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guice module that registers the KecongCommAdapterFactory with the openTCS kernel.
 */
public class KecongAdapterModule extends KernelInjectionModule {

    private static final Logger LOG = LoggerFactory.getLogger(KecongAdapterModule.class);

    @Override
    protected void configure() {
        LOG.info("Registering KecongCommAdapterFactory via Guice module");
        vehicleCommAdaptersBinder().addBinding().to(KecongCommAdapterFactory.class);
    }
}
