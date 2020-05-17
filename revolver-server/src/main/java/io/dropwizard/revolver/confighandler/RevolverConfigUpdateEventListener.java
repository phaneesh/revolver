package io.dropwizard.revolver.confighandler;

import static io.dropwizard.revolver.confighandler.DynamicConfigHandler.DEFAULT_CONFIG_HASH;
import static io.dropwizard.revolver.util.CommonUtils.computeHash;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.RevolverContextFactory;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverConfigHolder;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevolverConfigUpdateEventListener implements ConfigUpdateEventListener {

    private ObjectMapper objectMapper;

    private RevolverConfigHolder revolverConfigHolder;

    private String configAttribute;

    private ConfigLoadInfo configLoadInfo;

    public RevolverConfigUpdateEventListener(final String configAttribute, final ObjectMapper objectMapper,
            RevolverConfigHolder configHolder) {
        this.configAttribute = configAttribute;
        this.configLoadInfo = ConfigLoadInfo.builder()
                .previousConfigHash(DEFAULT_CONFIG_HASH)
                .previousLoadTime(new Date())
                .build();
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.revolverConfigHolder = configHolder;
    }

    @Override
    public void initConfigLoadInfo(Map<String, ConfigLoadInfo> initialConfigLoadInfos) {
        this.configLoadInfo = initialConfigLoadInfos.getOrDefault(configAttribute,
                        ConfigLoadInfo.builder()
                                .previousConfigHash(DEFAULT_CONFIG_HASH)
                                .previousLoadTime(new Date())
                                .build());
    }

    @Override
    public void configUpdated(ConfigUpdateEvent configUpdateEvent) {
        try {
            String configString = configUpdateEvent.getUpdatedConfig()
                    .get(configAttribute).toString();
            String currentConfigHash = computeHash(configString);
            log.info("Old Config Hash for {} : {} | New Config Hash: {}", configAttribute,
                    configLoadInfo.getPreviousConfigHash(), currentConfigHash);
            if (!configLoadInfo.getPreviousConfigHash().equals(currentConfigHash)) {
                reloadConfig(configString);
                configLoadInfo.setPreviousConfigHash(currentConfigHash);
                configLoadInfo.setPreviousLoadTime(new Date());
            }
        } catch (Exception e) {
            log.error("Error updating "+configAttribute+" configuration", e);
        }
    }

    private void reloadConfig(String configString) {
        try {
            RevolverConfig revolverConfig = objectMapper.readValue(configString, RevolverConfig.class);
            revolverConfigHolder.setConfig(revolverConfig);

            configUpdated(revolverConfig);
        } catch (IOException e) {
            log.error("Error reloading revolver config: ", e);
        }
    }

    public synchronized void configUpdated(RevolverConfig revolverConfig) {
        RevolverBundle.loadServiceConfiguration(revolverConfig);

        RevolverContextFactory revolverContextFactory = RevolverBundle.revolverContextFactory;
        for (RevolverExecutorType revolverExecutorType : RevolverExecutorType.values()) {
            Optional.ofNullable(revolverContextFactory.getContext(revolverExecutorType))
                    .ifPresent(revolverHttpContext -> revolverHttpContext.reload(revolverConfig));
        }
    }

}
