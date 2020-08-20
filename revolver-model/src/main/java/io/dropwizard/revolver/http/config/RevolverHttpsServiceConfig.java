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
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.discovery.EndpointSpec;
import io.dropwizard.revolver.http.auth.AuthConfig;
import io.dropwizard.revolver.splitting.RevolverHttpServiceSplitConfig;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;

/**
 * @author phaneesh
 */
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RevolverHttpsServiceConfig extends RevolverServiceConfig {

    @Builder
    public RevolverHttpsServiceConfig(String type,
                                      String service,
                                      EndpointSpec endpoint,
                                      int connectionPoolSize,
                                      boolean authEnabled,
                                      AuthConfig auth,
                                      String keyStorePath,
                                      String keystorePassword,
                                      @Singular("api") Set<RevolverHttpApiConfig> apis,
                                      boolean trackingHeaders,
                                      boolean compression,
                                      int connectionKeepAliveInMillis,
                                      RevolverHttpServiceSplitConfig serviceSplitConfig,
                                      SentinelCommandConfig sentinelCommandConfig,
                                      ThreadPoolGroupConfig threadPoolGroupConfig) {
        super(type, service, apis, connectionPoolSize, endpoint, authEnabled, auth, true, keystorePassword,
                keyStorePath, connectionKeepAliveInMillis, serviceSplitConfig, RevolverExecutorType.RESILIENCE,
                trackingHeaders, compression, threadPoolGroupConfig);
        this.setSentinelCommandConfig(sentinelCommandConfig);
    }
}