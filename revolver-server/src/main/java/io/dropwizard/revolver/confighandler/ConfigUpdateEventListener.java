package io.dropwizard.revolver.confighandler;

import static io.dropwizard.revolver.confighandler.DynamicConfigHandler.DEFAULT_CONFIG_HASH;
import static io.dropwizard.revolver.util.CommonUtils.computeHash;

import java.util.Date;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ConfigUpdateEventListener {

    private String configAttribute;

    private ConfigLoadInfo configLoadInfo;

    public ConfigUpdateEventListener(final String configAttribute) {
        this.configAttribute = configAttribute;
        this.configLoadInfo = ConfigLoadInfo.builder()
                .previousConfigHash(DEFAULT_CONFIG_HASH)
                .previousLoadTime(new Date())
                .build();
    }

    void configUpdated(ConfigUpdateEvent configUpdateEvent) {
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

    protected abstract void reloadConfig(String configString);

}
