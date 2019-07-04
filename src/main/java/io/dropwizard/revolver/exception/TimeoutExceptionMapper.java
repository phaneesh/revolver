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

package io.dropwizard.revolver.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.msgpack.MsgPackMediaType;
import java.util.concurrent.TimeoutException;
import javax.inject.Singleton;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * @author phaneesh
 */
@Provider
@Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK,
        MediaType.APPLICATION_XML})
@Singleton
public class TimeoutExceptionMapper implements ExceptionMapper<TimeoutException> {

    private ObjectMapper objectMapper;

    public TimeoutExceptionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Response toResponse(TimeoutException exception) {
        try {
            return Response.status(Response.Status.GATEWAY_TIMEOUT).entity(objectMapper
                    .writeValueAsBytes(ImmutableMap.builder().put("errorCode", "R000")
                            .put("message", "Service timeout").build())).build();
        } catch (Exception e) {
            return Response.serverError().entity("Server Error".getBytes()).build();
        }
    }
}
