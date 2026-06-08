package com.kecong.opentcs;

import com.google.inject.Provider;
import org.opentcs.components.kernel.services.InternalVehicleService;
import org.opentcs.customizations.kernel.KernelInjectionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guice module that registers the KecongCommAdapterFactory with the openTCS kernel.
 */
public class KecongAdapterModule extends KernelInjectionModule {

    private static final Logger LOG = LoggerFactory.getLogger(KecongAdapterModule.class);

    /** Lazily-resolved provider for kernel vehicle service */
    private static Provider<InternalVehicleService> vehicleServiceProvider;

    public static InternalVehicleService getVehicleService() {
        return vehicleServiceProvider != null ? vehicleServiceProvider.get() : null;
    }

    @Override
    protected void configure() {
        LOG.info("Registering KecongCommAdapterFactory via Guice module");
        vehicleCommAdaptersBinder().addBinding().to(KecongCommAdapterFactory.class);
        // Get the provider now; it will resolve lazily when get() is called later
        vehicleServiceProvider = getProvider(InternalVehicleService.class);
    }
}
