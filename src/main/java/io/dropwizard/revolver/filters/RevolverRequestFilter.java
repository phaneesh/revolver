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
package io.dropwizard.revolver.filters;

import com.google.common.base.Strings;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.annotation.Priority;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.UUID;

/**
 * @author phaneesh
 */
@Slf4j
@Provider
@Priority(Priorities.HEADER_DECORATOR)
@Singleton
public class RevolverRequestFilter implements ContainerRequestFilter {

    private final RevolverConfig config;

    public RevolverRequestFilter(RevolverConfig config) {
        this.config = config;
    }

    @Override
    public void filter(final ContainerRequestContext containerRequestContext) {
        if (!containerRequestContext.getUriInfo().getPath().startsWith("revolver/v1")) {
            String requestId = containerRequestContext.getHeaderString(RevolversHttpHeaders.REQUEST_ID_HEADER);
            val transactionId = containerRequestContext.getHeaderString(RevolversHttpHeaders.TXN_ID_HEADER);
            if (Strings.isNullOrEmpty(requestId)) {
                requestId = UUID.randomUUID().toString();
                containerRequestContext.getHeaders().add(RevolversHttpHeaders.REQUEST_ID_HEADER, requestId);
            }
            if (Strings.isNullOrEmpty(transactionId)) {
                containerRequestContext.getHeaders().add(RevolversHttpHeaders.TXN_ID_HEADER, requestId);
            }
            if (Strings.isNullOrEmpty(containerRequestContext.getHeaderString(RevolversHttpHeaders.TIMESTAMP_HEADER))) {
                containerRequestContext.getHeaders().add(RevolversHttpHeaders.TIMESTAMP_HEADER, Instant.now().toString());
            }
            //Default Accept & Content-Type to application/json
            if (Strings.isNullOrEmpty(containerRequestContext.getHeaderString(HttpHeaders.ACCEPT))) {
                containerRequestContext.getHeaders().add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
            }
            if (Strings.isNullOrEmpty(containerRequestContext.getHeaderString(HttpHeaders.CONTENT_TYPE))) {
                containerRequestContext.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            }
        } else {
            //Check if callback is enabled
            if (!Strings.isNullOrEmpty(containerRequestContext.getHeaderString(RevolversHttpHeaders.CALLBACK_URI_HEADER))) {
                //Add timeout header if it is absent
                if (Strings.isNullOrEmpty(containerRequestContext.getHeaderString(RevolversHttpHeaders.CALLBACK_TIMEOUT_HEADER))) {
                    containerRequestContext.getHeaders().add(RevolversHttpHeaders.CALLBACK_TIMEOUT_HEADER, String.valueOf(config.getCallbackTimeout()));
                }
                //Add callback method header if it is absent
                if (Strings.isNullOrEmpty(containerRequestContext.getHeaderString(RevolversHttpHeaders.CALLBACK_METHOD_HEADER))) {
                    containerRequestContext.getHeaders().add(RevolversHttpHeaders.CALLBACK_METHOD_HEADER, "POST");
                }
            }
        }
    }
}
