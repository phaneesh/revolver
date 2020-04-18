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

package io.dropwizard.revolver.http.config;

import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.ThreadPoolGroupConfig;
import io.dropwizard.revolver.core.config.sentinel.SentinelCommandConfig;
import io.dropwizard.revolver.discovery.EndpointSpec;
import io.dropwizard.revolver.http.auth.AuthConfig;
import io.dropwizard.revolver.splitting.RevolverHttpServiceSplitConfig;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;

/**
 * @author phaneesh
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RevolverHttpsServiceConfig extends RevolverServiceConfig {

    @NotNull
    @Valid
    private EndpointSpec endpoint;
    private int connectionPoolSize;
    private boolean authEnabled;
    private AuthConfig auth;
    private String keyStorePath;
    private String keystorePassword;
    @Singular("api")
    private Set<RevolverHttpApiConfig> apis;
    private boolean trackingHeaders;
    private boolean compression;
    private int connectionKeepAliveInMillis = 60000;
    private RevolverHttpServiceSplitConfig serviceSplitConfig;

    @Builder
    public RevolverHttpsServiceConfig(String type, String service,
            EndpointSpec enpoint, int connectionPoolSize, boolean authEnabled,
            AuthConfig auth, String keyStorePath, String keystorePassword,
            @Singular("api") Set<RevolverHttpApiConfig> apis, boolean trackingHeaders,
            boolean compression, int connectionKeepAliveInMillis,
            ThreadPoolGroupConfig threadPoolGroupConfig,
            RevolverHttpServiceSplitConfig serviceSplitConfig,
            SentinelCommandConfig sentinelCommandConfig) {
        super(type, service);
        this.setSentinelCommandConfig(sentinelCommandConfig);
        this.endpoint = enpoint;
        this.connectionPoolSize = connectionPoolSize;
        this.authEnabled = authEnabled;
        this.auth = auth;
        this.keyStorePath = keyStorePath;
        this.keystorePassword = keystorePassword;
        this.apis = apis;
        this.trackingHeaders = trackingHeaders;
        this.compression = compression;
        this.connectionKeepAliveInMillis = connectionKeepAliveInMillis;
        this.threadPoolGroupConfig = threadPoolGroupConfig;
        this.serviceSplitConfig = serviceSplitConfig;
    }
}