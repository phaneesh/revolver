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

package io.dropwizard.revolver.core.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.revolver.core.tracing.TraceInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * @author phaneesh
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "requestBuilder")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
public class RevolverRequest {

    @NotNull
    @NotEmpty
    private String type;

    @NotNull
    @NotEmpty
    private String service;

    @NotNull
    @NotEmpty
    private String api;

    @NotNull
    @Valid
    @Builder.Default
    private TraceInfo trace = new TraceInfo();

    @Builder.Default
    private RevolverExecutorType revolverExecutorType = RevolverExecutorType.RESILIENCE;

    public RevolverExecutorType getRevolverExecutorType() {
        if (revolverExecutorType == null) {
            return RevolverExecutorType.RESILIENCE;
        }
        return revolverExecutorType;
    }
}
