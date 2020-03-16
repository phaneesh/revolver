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

package io.dropwizard.revolver.http.model;

import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.tracing.TraceInfo;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author phaneesh
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RevolverHttpRequest extends RevolverRequest {

    private MultivaluedMap<String, String> headers;
    private MultivaluedMap<String, String> queryParams;
    private Map<String, String> pathParams;
    private String path;
    private RevolverHttpApiConfig.RequestMethod method;
    private byte[] body;
    private RoutingContext routingContext;

    public RevolverHttpRequest() {
        this.headers = new MultivaluedHashMap<>();
        this.queryParams = new MultivaluedHashMap<>();
        this.pathParams = Maps.newHashMap();
        this.method = RevolverHttpApiConfig.RequestMethod.GET;
        this.setType("http");
    }

    @Builder
    public RevolverHttpRequest(String service, String api,
            RevolverHttpApiConfig.RequestMethod method, TraceInfo traceInfo,
            MultivaluedMap<String, String> headers, MultivaluedMap<String, String> queryParams,
            Map<String, String> pathParams, String path, byte[] body, RevolverExecutorType revolverExecutorType,
            RoutingContext routingContext) {
        super("http", service, api, traceInfo, revolverExecutorType);
        this.headers = new MultivaluedHashMap<>();
        this.queryParams = new MultivaluedHashMap<>();
        this.pathParams = Maps.newHashMap();
        this.headers = headers;
        this.queryParams = queryParams;
        this.pathParams = pathParams;
        this.body = body;
        this.path = path;
        this.method = method;
        this.routingContext = routingContext;
    }
}
