package io.dropwizard.revolver.confighandler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.RevolverContextFactory;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverConfigHolder;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevolverConfigUpdateEventListener extends ConfigUpdateEventListener {

    private ObjectMapper objectMapper;

    private RevolverConfigHolder revolverConfigHolder;

    public RevolverConfigUpdateEventListener(final String configAttribute, final ObjectMapper objectMapper,
            RevolverConfigHolder configHolder) {
        super(configAttribute);
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.revolverConfigHolder = configHolder;
    }

    @Override
    protected void reloadConfig(String configString) {
        try {
            RevolverConfig revolverConfig = objectMapper.readValue(configString, RevolverConfig.class);
            revolverConfigHolder.setConfig(revolverConfig);

            configUpdated(revolverConfig);
        } catch (IOException e) {
            log.error("Error reloading revolver config: ", e);
        }
    }

    public synchronized void configUpdated(RevolverConfig revolverConfig) {
        RevolverContextFactory revolverContextFactory = RevolverBundle.revolverContextFactory;
        for (RevolverExecutorType revolverExecutorType : RevolverExecutorType.values()) {
            revolverContextFactory.getContext(revolverExecutorType).reload(revolverConfig);
        }
        RevolverBundle.loadServiceConfiguration(revolverConfig);
    }

}
