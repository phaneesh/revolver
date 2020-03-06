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

package io.dropwizard.revolver.discovery.model;

import io.dropwizard.revolver.discovery.EndpointSpec;
import io.dropwizard.revolver.discovery.SpecVisitor;
import lombok.Getter;
import lombok.Setter;

/**
 * @author phaneesh
 */
public class RangerEndpointSpec extends EndpointSpec {

    @Getter
    @Setter
    private String service;

    @Getter
    @Setter
    private String environment;

    public RangerEndpointSpec() {
        super(EndpointSpecType.ranger_sharded);
    }

    public RangerEndpointSpec(final String service, final String environment) {
        super(EndpointSpecType.ranger_sharded);
        this.service = service;
        this.environment = environment;
    }


    @Override
    public void accept(final SpecVisitor visitor) {
        visitor.visit(this);
    }
}
