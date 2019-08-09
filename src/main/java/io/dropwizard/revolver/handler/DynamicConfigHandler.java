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

package io.dropwizard.revolver.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.RevolverConfig;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

@Slf4j
public class DynamicConfigHandler implements Managed {

    private RevolverConfig revolverConfig;

    private ConfigSource configSource;

    private ScheduledExecutorService scheduledExecutorService;

    private ObjectMapper objectMapper;

    private String configAttribute;

    private String prevConfigHash;

    private long prevLoadTime;

    private RevolverBundle revolverBundle;

    public DynamicConfigHandler(String configAttribute, RevolverConfig revolverConfig,
            ObjectMapper objectMapper, ConfigSource configSource, RevolverBundle revolverBundle) {
        this.configAttribute = configAttribute;
        this.revolverConfig = revolverConfig;
        this.configSource = configSource;
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.revolverBundle = revolverBundle;
        try {
            if (configSource == null) {
                prevConfigHash = "unknown";
            } else {
                prevConfigHash = computeHash(loadConfigData(false));
            }
            log.info("Initializing dynamic config handler... Config Hash: {}", prevConfigHash);
        } catch (Exception e) {
            log.error("Error fetching configuration", e);
        }
    }


    @Override
    public void start() {
        scheduledExecutorService.scheduleWithFixedDelay(this::refreshConfig, 120,
                revolverConfig.getConfigPollIntervalSeconds(), TimeUnit.SECONDS);
    }

    public String refreshConfig() {
        if (configSource == null) {
            return "unknown";
        }
        try {
            String substituted = loadConfigData(false);
            String curHash = computeHash(substituted);
            log.info("Old Config Hash: {} | New Config Hash: {}", prevConfigHash, curHash);
            if (!prevConfigHash.equals(curHash)) {
                log.info("Refreshing config with new hash: {}", curHash);
                RevolverConfig revolverConfig = objectMapper
                        .readValue(substituted, RevolverConfig.class);
                RevolverBundle.loadServiceConfiguration(revolverConfig);
                this.prevConfigHash = curHash;
                prevLoadTime = System.currentTimeMillis();
                revolverBundle.onConfigChange(loadConfigData(true));
                return prevConfigHash;
            } else {
                log.info("No config changes detected. Not reloading config..");
                return prevConfigHash;
            }
        } catch (Exception e) {
            log.error("Error fetching configuration", e);
            return null;
        }
    }

    public Map<String, Object> configLoadInfo() {
        return ImmutableMap.<String, Object>builder().put("hash", prevConfigHash)
                .put("loadTime", new Date(prevLoadTime)).build();
    }

    private String loadConfigData(boolean fullConfig) throws Exception {
        log.info(
                "Fetching configuration from config source. Current Hash: {} | Previous fetch time: {}",
                prevConfigHash, new Date(prevLoadTime));
        JsonNode node = objectMapper
                .readTree(new YAMLFactory().createParser(configSource.loadConfigData()));
        EnvironmentVariableSubstitutor substitute = new EnvironmentVariableSubstitutor(false, true);
        if (fullConfig) {
            return substitute.replace(node.toString());
        }
        return substitute.replace(node.get(configAttribute).toString());
    }

    private String computeHash(String config) {
        return DigestUtils.sha512Hex(config);
    }

    @Override
    public void stop() {
        scheduledExecutorService.shutdown();
    }
}
