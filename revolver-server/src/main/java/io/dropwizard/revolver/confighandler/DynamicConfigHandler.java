/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.dropwizard.revolver.confighandler;

import static io.dropwizard.revolver.util.CommonUtils.computeHash;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.ByteStreams;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.RevolverConfigHolder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicConfigHandler implements Managed {

    public static final String DEFAULT_CONFIG_HASH = "unknown";

    private static List<ConfigUpdateEventListener> configUpdateEventListeners = new ArrayList<>();

    private RevolverConfigHolder revolverConfigHolder;

    private ConfigSource configSource;

    private ScheduledExecutorService scheduledExecutorService;

    @Getter
    private ConfigLoadInfo configLoadInfo;

    private ObjectMapper objectMapper;

    private RevolverBundle revolverBundle;

    private static Map<String, ConfigLoadInfo> initialConfigLoadInfos = new HashMap<>();

    public DynamicConfigHandler(RevolverConfigHolder revolverConfigHolder,
            ObjectMapper objectMapper, ConfigSource configSource, RevolverBundle revolverBundle) {
        this.revolverConfigHolder = revolverConfigHolder;
        this.configSource = configSource;
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.revolverBundle = revolverBundle;
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.configLoadInfo = new ConfigLoadInfo(new Date());
        try {
            if (configSource == null) {
                configLoadInfo.setPreviousConfigHash(DEFAULT_CONFIG_HASH);
            } else {
                JsonNode appConfig = loadConfigData();

                String initialHash = computeHash(appConfig.toString());
                configLoadInfo.setPreviousConfigHash(initialHash);

                appConfig.fields()
                        .forEachRemaining((configAttribute) ->
                                initialConfigLoadInfos.put(configAttribute.getKey(),
                                        ConfigLoadInfo.builder()
                                                .previousLoadTime(new Date())
                                                .previousConfigHash(computeHash(configAttribute.getValue().toString()))
                                                .build()
                                )
                        );
            }
            log.info("Initializing dynamic config handler... Config Hash: {}", configLoadInfo.getPreviousConfigHash());
        } catch (Exception e) {
            log.error("Error initializing dynamic config handler", e);
        }
    }

    @Override
    public void start() {
        scheduledExecutorService.scheduleWithFixedDelay(this::refreshConfig, 120,
                revolverConfigHolder.getConfig().getConfigPollIntervalSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
    }

    public String refreshConfig() {
        if (configSource == null) {
            return DEFAULT_CONFIG_HASH;
        }
        try {
            JsonNode appConfig = loadConfigData();
            String curHash = computeHash(appConfig.toString());
            log.info("Old Config Hash: {} | New Config Hash: {}", configLoadInfo.getPreviousConfigHash(), curHash);
            if (!configLoadInfo.getPreviousConfigHash().equals(curHash)) {
                log.info("Refreshing config with new hash: {}", curHash);
                notifyListeners(ConfigUpdateEvent.builder()
                        .updatedConfig(appConfig)
                        .updatedAt(new Date())
                        .build());

                configLoadInfo.setPreviousConfigHash(curHash);
                configLoadInfo.setPreviousLoadTime(new Date());

                revolverBundle.onConfigChange(appConfig.toString());
                return configLoadInfo.getPreviousConfigHash();
            } else {
                log.info("No config changes detected. Not reloading config..");
                return configLoadInfo.getPreviousConfigHash();
            }
        } catch (Exception e) {
            log.error("Error fetching configuration", e);
            return null;
        }
    }

    private JsonNode loadConfigData() throws Exception {
        log.info(
                "Fetching configuration from config source. Current Hash: {} | Previous fetch time: {}",
                configLoadInfo.getPreviousConfigHash(), configLoadInfo.getPreviousLoadTime());
        InputStream inputStream = configSource.loadConfigData();

        final String config = new String(ByteStreams.toByteArray(inputStream), StandardCharsets.UTF_8);
        EnvironmentVariableSubstitutor substitutor = new EnvironmentVariableSubstitutor(false, true);
        final String substituted = substitutor.replace(config);

        return objectMapper.readTree(new YAMLFactory().createParser(substituted));
    }

    private static void notifyListeners(ConfigUpdateEvent configUpdateEvent) {
        configUpdateEventListeners
                .forEach(configUpdateEventListener -> {
                    try {
                        configUpdateEventListener.configUpdated(configUpdateEvent);
                    } catch (Exception e) {
                        log.error("Error while notifying config update event to listener: {}",
                                configUpdateEventListener, e);
                    }
                });
    }

    public static void registerConfigUpdateEventListener(ConfigUpdateEventListener configUpdateEventListener) {
        try {
            configUpdateEventListeners.add(configUpdateEventListener);
            configUpdateEventListener.initConfigLoadInfo(initialConfigLoadInfos);
        } catch (Exception e) {
            log.error("Error while registering config update event listener: {}",
                    configUpdateEventListener, e);
        }
    }

    public static void registerConfigUpdateEventListeners(List<ConfigUpdateEventListener> eventListeners) {
        configUpdateEventListeners.addAll(eventListeners);
    }
}
