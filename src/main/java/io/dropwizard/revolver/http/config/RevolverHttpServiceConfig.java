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
import io.dropwizard.revolver.discovery.EndpointSpec;
import io.dropwizard.revolver.http.auth.AuthConfig;
import io.dropwizard.revolver.splitting.RevolverHttpServiceSplitConfig;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * @author phaneesh
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class RevolverHttpServiceConfig extends RevolverServiceConfig {

    @NotNull
    @Valid
    private EndpointSpec endpoint;
    private int connectionPoolSize;
    private boolean authEnabled;
    private boolean secured;
    private AuthConfig auth;
    private String keyStorePath;
    private String keystorePassword;
    @Singular("api")
    private Set<RevolverHttpApiConfig> apis;
    private boolean trackingHeaders;
    private boolean compression;
    private int connectionKeepAliveInMillis = 30000;
    private RevolverHttpServiceSplitConfig serviceSplitConfig;

    @Builder
    public RevolverHttpServiceConfig(final String type, final String service, final EndpointSpec enpoint, final int connectionPoolSize,
                                     final boolean authEnabled, final boolean secured, final AuthConfig auth, final String keyStorePath,
                                     final String keystorePassword, @Singular("api") final Set<RevolverHttpApiConfig> apis, final boolean trackingHeaders,
                                     final boolean compression, final int connectionKeepAliveInMillis,
                                     ThreadPoolGroupConfig threadPoolGroupConfig, final RevolverHttpServiceSplitConfig serviceSplitConfig) {
        super(type, service);
        this.endpoint = enpoint;
        this.connectionPoolSize = connectionPoolSize;
        this.authEnabled = authEnabled;
        this.auth = auth;
        this.secured = secured;
        this.keyStorePath = keyStorePath;
        this.keystorePassword = keystorePassword;
        this.apis = apis;
        this.trackingHeaders = trackingHeaders;
        this.compression = compression;
        this.connectionKeepAliveInMillis = connectionKeepAliveInMillis;
        this.threadPoolGroupConfig = threadPoolGroupConfig;
        this.serviceSplitConfig = serviceSplitConfig;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o)
            return true;
        if(!(o instanceof RevolverHttpServiceConfig))
            return false;
        if(!super.equals(o))
            return false;

        RevolverHttpServiceConfig that = (RevolverHttpServiceConfig)o;

        if(connectionPoolSize != that.connectionPoolSize)
            return false;
        if(authEnabled != that.authEnabled)
            return false;
        if(secured != that.secured)
            return false;
        if(trackingHeaders != that.trackingHeaders)
            return false;
        if(compression != that.compression)
            return false;
        if(connectionKeepAliveInMillis != that.connectionKeepAliveInMillis)
            return false;
        if(!endpoint.equals(that.endpoint))
            return false;
        if(!auth.equals(that.auth))
            return false;
        if(!keyStorePath.equals(that.keyStorePath))
            return false;
        if(!keystorePassword.equals(that.keystorePassword))
            return false;
        if(!apis.equals(that.apis))
            return false;
        return serviceSplitConfig.equals(that.serviceSplitConfig);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + endpoint.hashCode();
        result = 31 * result + connectionPoolSize;
        result = 31 * result + (authEnabled ? 1 : 0);
        result = 31 * result + (secured ? 1 : 0);
        result = 31 * result + auth.hashCode();
        result = 31 * result + keyStorePath.hashCode();
        result = 31 * result + keystorePassword.hashCode();
        result = 31 * result + apis.hashCode();
        result = 31 * result + (trackingHeaders ? 1 : 0);
        result = 31 * result + (compression ? 1 : 0);
        result = 31 * result + connectionKeepAliveInMillis;
        result = 31 * result + serviceSplitConfig.hashCode();
        return result;
    }
}
