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

import lombok.*;

import java.util.Set;

/**
 * A metadata element that can be used to supply authorization info to any external authorization system
 * for basic role based authorizations
 *
 * @author phaneesh
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RevolverHttpAuthorizationConfig {

    private String namespace;

    @Builder.Default
    private String type = "dynamic";

    @Singular
    private Set<String> methods;

    @Singular
    private Set<String> roles;

    @Singular
    private Set<String> permissions;
}
