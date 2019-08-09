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

package io.dropwizard.revolver.core;

import io.dropwizard.revolver.core.config.ClientConfig;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.RuntimeConfig;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;

/**
 * @author phaneesh
 */
public abstract class SimpleRevolverCommand<RequestType extends RevolverRequest, ResponseType extends RevolverResponse, ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig, CommandHandlerConfigurationType extends CommandHandlerConfig> extends
        RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> {

    public SimpleRevolverCommand(ContextType context, ClientConfig clientConfiguration,
            RuntimeConfig runtimeConfig, ServiceConfigurationType serviceConfiguration,
            CommandHandlerConfigurationType apiConfiguration) {
        super(context, clientConfiguration, runtimeConfig, serviceConfiguration, apiConfiguration);
    }
}

