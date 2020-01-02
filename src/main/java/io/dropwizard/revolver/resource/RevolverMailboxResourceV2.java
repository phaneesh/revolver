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

package io.dropwizard.revolver.resource;

import com.codahale.metrics.annotation.Metered;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.dropwizard.msgpack.MsgPackMediaType;
import io.dropwizard.revolver.base.core.RevolverAckMessage;
import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.base.core.RevolverCallbackResponses;
import io.dropwizard.revolver.base.core.RevolverRequestState;
import io.dropwizard.revolver.base.core.RevolverRequestStateResponse;
import io.dropwizard.revolver.exception.RevolverException;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.HeaderUtil;
import io.dropwizard.revolver.util.ResponseTransformationUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * @author phaneesh
 */
@Path("/revolver")
@Slf4j
@Data
@AllArgsConstructor
@Builder
@Singleton
@Api(value = "MailBox APIs V2", description = "Revolver gateway api v2 for interacting mailbox requests")
public class RevolverMailboxResourceV2 {

    private static final RevolverException NOT_FOUND_ERROR = RevolverException.builder()
            .status(Response.Status.NOT_FOUND.getStatusCode()).message("Not found")
            .errorCode("R002").build();
    private static final RevolverException SERVER_ERROR = RevolverException.builder()
            .status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()).errorCode("R001")
            .message("Oops! Something went wrong!").build();
    private PersistenceProvider persistenceProvider;
    private ObjectMapper jsonObjectMapper;
    private ObjectMapper msgPackObjectMapper;
    private Map<String, RevolverHttpApiConfig> apiConfig;

    @Path("/v2/requests")
    @GET
    @Metered
    @ApiOperation(value = "Get all the requests in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK,
            MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response requests(
            @HeaderParam(RevolversHttpHeaders.MAILBOX_AUTH_ID_HEADER) String mailboxAuthId,
            @Context HttpHeaders headers) throws RevolverException {
        try {
            List<RevolverCallbackRequest> callbackRequests = persistenceProvider
                    .requestsByMailboxAuth(mailboxAuthId);
            if (callbackRequests == null) {
                throw NOT_FOUND_ERROR;
            }
            if (headers.getAcceptableMediaTypes().size() == 0) {
                return Response.ok(ResponseTransformationUtil
                        .transform(callbackRequests, MediaType.APPLICATION_JSON, jsonObjectMapper,
                                msgPackObjectMapper), MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(ResponseTransformationUtil.transform(callbackRequests,
                    headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper,
                    msgPackObjectMapper), headers.getAcceptableMediaTypes().get(0).toString())
                    .build();
        } catch (Exception e) {
            log.error("Error getting requests", e);
            throw SERVER_ERROR;

        }
    }

    @Path("/v2/responses")
    @GET
    @Metered
    @ApiOperation(value = "Get all the responses in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK,
            MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response responses(
            @HeaderParam(RevolversHttpHeaders.MAILBOX_AUTH_ID_HEADER) String mailboxAuthId,
            @Context HttpHeaders headers) throws RevolverException {
        try {
            if (Strings.isNullOrEmpty(mailboxAuthId)) {
                throw RevolverException.builder()
                        .status(Response.Status.BAD_REQUEST.getStatusCode())
                        .message("Invalid Mailbox Id").errorCode("R003").build();
            }
            List<RevolverCallbackResponses> callbackResponses = persistenceProvider
                    .responsesByMailboxAuth(mailboxAuthId);
            if (callbackResponses == null) {
                throw NOT_FOUND_ERROR;
            }
            if (headers.getAcceptableMediaTypes().size() == 0) {
                return Response.ok(ResponseTransformationUtil
                        .transform(callbackResponses, MediaType.APPLICATION_JSON, jsonObjectMapper,
                                msgPackObjectMapper), MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(ResponseTransformationUtil.transform(callbackResponses,
                    headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper,
                    msgPackObjectMapper), headers.getAcceptableMediaTypes().get(0).toString())
                    .build();
        } catch (Exception e) {
            log.error("Error getting responses", e);
            throw RevolverException.builder()
                    .status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()).errorCode("R001")
                    .message(ExceptionUtils.getRootCause(e).getMessage()).build();

        }
    }

}
