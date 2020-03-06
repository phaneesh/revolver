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
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.NotBlank;

/**
 * @author phaneesh
 */
public class SimpleEndpointSpec extends EndpointSpec {

    @Getter
    @Setter
    @NotBlank
    private String host;

    @Getter
    @Setter
    private int port;

    public SimpleEndpointSpec() {
        super(EndpointSpecType.simple);
    }

    @Builder
    public SimpleEndpointSpec(EndpointSpecType type, String host, int port) {
        super(type);
        this.host = host;
        this.port = port;
    }

    @Override
    public void accept(SpecVisitor visitor) {
        visitor.visit(this);
    }
}
