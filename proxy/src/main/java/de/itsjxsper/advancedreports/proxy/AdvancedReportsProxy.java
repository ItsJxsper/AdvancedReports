package de.itsjxsper.advancedreports.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import org.slf4j.Logger;

public class AdvancedReportsProxy {

    @Inject
    private Logger logger;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Plugin initialization logic goes here
    }
}
