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

import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.splitting.RevolverHttpApiSplitConfig;
import io.dropwizard.revolver.retry.RevolverApiRetryConfig;
import lombok.*;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.Set;

/**
 * @author phaneesh
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class RevolverHttpApiConfig extends CommandHandlerConfig {

    @NotNull
    @NotEmpty
    private String path;

    private boolean async = false;

    private boolean whitelist = false;

    private String acceptType = MediaType.APPLICATION_JSON;

    private  String acceptEncoding = "identity";

    private RevolverApiRetryConfig retryConfig;

    private RevolverHttpApiSplitConfig splitConfig;

    @NotNull
    @NotEmpty
    @Singular
    private Set<RequestMethod> methods = Collections.singleton(RequestMethod.GET);

    private Set<Integer> acceptableResponseCodes = Collections.emptySet();

    private Set<RevolverHttpAuthorizationConfig> authorizations;

    private RevolverHttpAuthorizationConfig authorization;

    @Builder(builderMethodName = "configBuilder")
    public RevolverHttpApiConfig(final String api, final HystrixCommandConfig runtime, final String path,
                                 @Singular final Set<RequestMethod> methods, final Set<Integer>
                                             acceptableResponseCodes, final boolean sharedPool, RevolverHttpApiSplitConfig splitConfig,
                                 final RevolverApiRetryConfig retryConfig) {
        super(api, sharedPool, runtime);
        this.path = path;
        this.methods = methods;
        this.acceptableResponseCodes = acceptableResponseCodes;
        this.retryConfig = retryConfig;
        this.splitConfig = splitConfig;
    }

    public enum RequestMethod {
        GET,
        POST,
        PUT,
        DELETE,
        HEAD,
        PATCH,
        OPTIONS
    }
}
