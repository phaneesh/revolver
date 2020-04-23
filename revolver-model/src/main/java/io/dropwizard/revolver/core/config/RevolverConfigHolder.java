package io.dropwizard.revolver.core.config;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class RevolverConfigHolder {

    private AtomicReference<RevolverConfig> configReference;
    private final AtomicLong lastUpdatedTimestamp;

    public RevolverConfigHolder(RevolverConfig initialRevolverConfig) {
        this.configReference = new AtomicReference<>(initialRevolverConfig);
        this.lastUpdatedTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    public RevolverConfig getConfig() {
        return configReference.get();
    }

    public void setConfig(RevolverConfig config) {
        configReference.set(config);
        this.lastUpdatedTimestamp.set(System.currentTimeMillis());
    }
}
