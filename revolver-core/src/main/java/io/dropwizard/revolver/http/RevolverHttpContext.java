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

package io.dropwizard.revolver.http;


import com.codahale.metrics.MetricRegistry;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.setup.Environment;

/**
 * @author phaneesh
 */
public class RevolverHttpContext extends RevolverContext {

    public void initialize(Environment environment, RevolverConfig revolverConfig,
            MetricRegistry metrics) {

    }

    public RevolverExecutorType getExecutorType() {
        return RevolverExecutorType.NONE;
    }

    public void reload(RevolverConfig revolverConfig) {

    }
}
