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
import io.dropwizard.revolver.util.ResponseTransformationUtil;

import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Map;

/**
 * @author phaneesh
 */
@Provider
@Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML})
public class RevolverExceptionMapper implements ExceptionMapper<RevolverException> {

    private ObjectMapper jsonObjectMapper;

    private ObjectMapper msgPackObjectMapper;

    @Context
    private HttpHeaders headers;

    public RevolverExceptionMapper(ObjectMapper objectMapper, ObjectMapper msgPackObjectMapper) {
        this.jsonObjectMapper = objectMapper;
        this.msgPackObjectMapper = msgPackObjectMapper;
    }

    @Override
    public Response toResponse(RevolverException exception) {
        Map response = ImmutableMap.builder().put("errorCode", exception.getErrorCode()).put("message", exception.getMessage()).build();
        try {
            if (headers.getAcceptableMediaTypes().size() == 0) {
                return Response.ok(ResponseTransformationUtil.transform(response, MediaType.APPLICATION_JSON, jsonObjectMapper, msgPackObjectMapper), MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(ResponseTransformationUtil.transform(response, headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper, msgPackObjectMapper), headers.getAcceptableMediaTypes().get(0).toString()).build();
        } catch (Exception e) {
            return Response.serverError().entity("Server Error".getBytes()).build();
        }
    }
}
