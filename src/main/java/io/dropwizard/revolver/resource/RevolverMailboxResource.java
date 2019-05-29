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
import io.dropwizard.revolver.base.core.*;
import io.dropwizard.revolver.exception.RevolverException;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.HeaderUtil;
import io.dropwizard.revolver.util.ResponseTransformationUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author phaneesh
 */
@Path("/revolver")
@Slf4j
@Data
@AllArgsConstructor
@Builder
@Singleton
@Api(value = "MailBox", description = "Revolver gateway api for interacting mailbox requests")
public class RevolverMailboxResource {

    private PersistenceProvider persistenceProvider;

    private ObjectMapper jsonObjectMapper;

    private ObjectMapper msgPackObjectMapper;

    private Map<String, RevolverHttpApiConfig> apiConfig;

    private static final RevolverException NOT_FOUND_ERROR = RevolverException.builder()
            .status(Response.Status.NOT_FOUND.getStatusCode())
            .message("Not found")
            .errorCode("R002")
            .build();

    private static final RevolverException SERVER_ERROR = RevolverException.builder()
            .status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
            .errorCode("R001")
            .message("Oops! Something went wrong!").build();


    @Path("/v1/request/status/{requestId}")
    @GET
    @Metered
    @ApiOperation(value = "Get the status of the request in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response requestStatus(@PathParam("requestId") final String requestId, @Context final HttpHeaders headers) throws RevolverException {
        try {
            RevolverRequestState state = persistenceProvider.requestState(requestId);
            if (state == null) {
                throw NOT_FOUND_ERROR;
            }
            RevolverRequestStateResponse response = RevolverRequestStateResponse.builder()
                    .requestId(requestId)
                    .state(state.name())
                    .build();
            if (headers.getAcceptableMediaTypes().size() == 0) {
                return Response.ok(ResponseTransformationUtil.transform(response,
                        MediaType.APPLICATION_JSON, jsonObjectMapper, msgPackObjectMapper),
                        MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(ResponseTransformationUtil.transform(response,
                    headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper, msgPackObjectMapper),
                    headers.getAcceptableMediaTypes().get(0).toString()).build();
        } catch (Exception e) {
            log.error("Error getting request state", e);
            throw SERVER_ERROR;
        }
    }

    @Path("/v1/request/ack/{requestId}")
    @POST
    @Metered
    @ApiOperation(value = "Send ack for a request so that the mailbox message can be marked as read")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response ack(@PathParam("requestId") final String requestId) throws RevolverException {
        try {
            RevolverRequestState state = persistenceProvider.requestState(requestId);
            if (state == null) {
                throw NOT_FOUND_ERROR;
            }
            switch (state) {
                case RESPONDED:
                case ERROR:
                    RevolverCallbackRequest callbackRequest = persistenceProvider.request(requestId);
                    List<String> ttl = callbackRequest.getHeaders().getOrDefault(RevolversHttpHeaders.MAILBOX_TTL_HEADER, Collections.emptyList());
                    int mailboxTtl = HeaderUtil.getTTL(callbackRequest);
                    if(!ttl.isEmpty()) {
                        mailboxTtl = Integer.parseInt(ttl.get(0));
                    }
                    persistenceProvider.setRequestState(requestId, RevolverRequestState.READ, mailboxTtl);
                    return Response.accepted().build();
                default:
                    return Response.status(Response.Status.BAD_REQUEST).build();
            }
        } catch (Exception e) {
            log.error("Error getting request state", e);
            throw SERVER_ERROR;
        }
    }

    @Path("/v1/request/{requestId}")
    @GET
    @Metered
    @ApiOperation(value = "Get the request in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response request(@PathParam("requestId") final String requestId, @Context final HttpHeaders headers) throws RevolverException {
        try {
            RevolverCallbackRequest callbackRequest = persistenceProvider.request(requestId);
            if (callbackRequest == null) {
                throw NOT_FOUND_ERROR;
            }
            if (headers.getAcceptableMediaTypes().size() == 0) {
                return Response.ok(ResponseTransformationUtil.transform(callbackRequest,
                        MediaType.APPLICATION_JSON, jsonObjectMapper, msgPackObjectMapper),
                        MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(ResponseTransformationUtil.transform(callbackRequest,
                    headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper, msgPackObjectMapper),
                    headers.getAcceptableMediaTypes().get(0).toString()).build();
        } catch (Exception e) {
            log.error("Error getting request", e);
            throw SERVER_ERROR;
        }
    }

    @Path("/v1/response/{requestId}")
    @GET
    @Metered
    @ApiOperation(value = "Get the response for a request in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response response(@PathParam("requestId") final String requestId) throws RevolverException {
        try {
            RevolverCallbackResponse callbackResponse = persistenceProvider.response(requestId);
            if (callbackResponse == null) {
                throw NOT_FOUND_ERROR;
            }
            val response = Response.status(callbackResponse.getStatusCode())
                    .entity(callbackResponse.getBody());
            callbackResponse.getHeaders().forEach((k, v) -> v.forEach(h -> response.header(k, h)));
            return response.build();
        } catch (Exception e) {
            log.error("Error getting response", e);
            throw SERVER_ERROR;
        }
    }

    @Path("/v2/response/{requestId}")
    @GET
    @Metered
    @ApiOperation(value = "Get the response for a request in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response getResponse(@PathParam("requestId") final String requestId,  @Context final HttpHeaders headers) throws RevolverException {
        try {
            RevolverRequestState state = persistenceProvider.requestState(requestId);
            if (state == null) {
                throw NOT_FOUND_ERROR;
            }
            switch (state){
                case RESPONDED:
                    RevolverCallbackResponse callbackResponse = persistenceProvider.response(requestId);
                    if (callbackResponse == null) {
                        throw NOT_FOUND_ERROR;
                    }
                    val response = Response.status(callbackResponse.getStatusCode())
                            .entity(callbackResponse.getBody());
                    callbackResponse.getHeaders().forEach((k, v) -> v.forEach(h -> response.header(k, h)));
                    return response.build();

                default:
                    RevolverRequestStateResponse revolverRequestStateResponse = RevolverRequestStateResponse.builder()
                            .requestId(requestId)
                            .state(state.name())
                            .build();
                    double retryAfter = getRetryAfter(requestId);
                    if (headers.getAcceptableMediaTypes().size() == 0) {
                        return Response.ok(ResponseTransformationUtil.transform(revolverRequestStateResponse,
                                                                                MediaType.APPLICATION_JSON, jsonObjectMapper, msgPackObjectMapper),
                                           MediaType.APPLICATION_JSON).header(RevolversHttpHeaders.RETRY_AFTER, retryAfter).build();
                    }
                    return Response.ok(ResponseTransformationUtil.transform(revolverRequestStateResponse,
                                                                            headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper, msgPackObjectMapper),
                                       headers.getAcceptableMediaTypes().get(0).toString()).build();

            }
        } catch (Exception e) {
            log.error("Error getting response", e);
            throw SERVER_ERROR;
        }
    }

    private double getRetryAfter(@PathParam("requestId") String requestId) {
        RevolverCallbackRequest revolverCallbackRequest = persistenceProvider.request(requestId);
        RevolverHttpApiConfig revolverHttpApiConfig = apiConfig.get(revolverCallbackRequest.getApi());
        double retryAfter;
        if(revolverHttpApiConfig == null || revolverHttpApiConfig.getApiLatencyConfig() == null){
            retryAfter = -1.0;
        }else {
            retryAfter  = revolverHttpApiConfig.getApiLatencyConfig().getLatency();
        }
        return retryAfter;
    }

    @Path("/v1/requests")
    @GET
    @Metered
    @ApiOperation(value = "Get all the requests in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response requests(@HeaderParam(RevolversHttpHeaders.MAILBOX_ID_HEADER) final String mailboxId, @Context final HttpHeaders headers) throws RevolverException {
        try {
            List<RevolverCallbackRequest> callbackRequests = persistenceProvider.requests(mailboxId);
            if (callbackRequests == null) {
                throw NOT_FOUND_ERROR;
            }
            if (headers.getAcceptableMediaTypes().size() == 0) {
                return Response.ok(ResponseTransformationUtil.transform(callbackRequests,
                        MediaType.APPLICATION_JSON, jsonObjectMapper, msgPackObjectMapper),
                        MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(ResponseTransformationUtil.transform(callbackRequests,
                    headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper, msgPackObjectMapper),
                    headers.getAcceptableMediaTypes().get(0).toString()).build();
        } catch (Exception e) {
            log.error("Error getting requests", e);
            throw SERVER_ERROR;

        }
    }

    @Path("/v1/responses")
    @GET
    @Metered
    @ApiOperation(value = "Get all the responses in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response responses(@HeaderParam(RevolversHttpHeaders.MAILBOX_ID_HEADER) final String mailboxId, @Context final HttpHeaders headers) throws RevolverException {
        try {
            if (Strings.isNullOrEmpty(mailboxId)) {
                throw RevolverException.builder()
                        .status(Response.Status.BAD_REQUEST.getStatusCode())
                        .message("Invalid Mailbox Id")
                        .errorCode("R003")
                        .build();
            }
            List<RevolverCallbackResponses> callbackResponses = persistenceProvider.responses(mailboxId);
            if (callbackResponses == null) {
                throw NOT_FOUND_ERROR;
            }
            if (headers.getAcceptableMediaTypes().size() == 0) {
                return Response.ok(ResponseTransformationUtil.transform(callbackResponses,
                        MediaType.APPLICATION_JSON, jsonObjectMapper, msgPackObjectMapper),
                        MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(ResponseTransformationUtil.transform(callbackResponses,
                    headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper, msgPackObjectMapper),
                    headers.getAcceptableMediaTypes().get(0).toString()).build();
        } catch (Exception e) {
            log.error("Error getting responses", e);
            throw RevolverException.builder()
                    .status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
                    .errorCode("R001")
                    .message(ExceptionUtils.getRootCause(e).getMessage()).build();

        }
    }

    @Path("/v1/message/persist")
    @POST
    @Metered
    @ApiOperation(value = "Persist a request in the mailbox")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML, MediaType.TEXT_HTML})
    public Response persistRequest(@Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws RevolverException {
        try {
            val requestId = headers.getHeaderString(RevolversHttpHeaders.REQUEST_ID_HEADER);
            val mailBoxId = headers.getHeaderString(RevolversHttpHeaders.MAILBOX_ID_HEADER);
            persistenceProvider.saveRequest(requestId, mailBoxId,
                    RevolverCallbackRequest.builder()
                            .api("persist")
                            .mode("POLLING")
                            .callbackUri(null)
                            .method("POST")
                            .service("mailbox")
                            .path(uriInfo.getPath())
                            .headers(headers.getRequestHeaders())
                            .queryParams(uriInfo.getQueryParameters())
                            .body(body)
                            .build()
            );
            RevolverAckMessage response = RevolverAckMessage.builder().requestId(requestId).acceptedAt(Instant.now().toEpochMilli()).build();
            if (headers.getAcceptableMediaTypes().size() == 0) {
                return Response.ok(ResponseTransformationUtil.transform(response,
                        MediaType.APPLICATION_JSON, jsonObjectMapper, msgPackObjectMapper),
                        MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(ResponseTransformationUtil.transform(response,
                    headers.getAcceptableMediaTypes().get(0).toString(), jsonObjectMapper, msgPackObjectMapper),
                    headers.getAcceptableMediaTypes().get(0).toString()).build();
        } catch (Exception e) {
            log.error("Error getting responses", e);
            throw SERVER_ERROR;
        }
    }
}
