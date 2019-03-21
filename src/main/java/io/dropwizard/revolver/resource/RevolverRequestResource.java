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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Metered;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.dropwizard.jersey.PATCH;
import io.dropwizard.msgpack.MsgPackMediaType;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.base.core.RevolverAckMessage;
import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.base.core.RevolverRequestState;
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.core.tracing.TraceInfo;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import io.dropwizard.revolver.http.model.RevolverHttpResponse;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.ResponseTransformationUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * @author phaneesh
 */
@Path("/apis")
@Slf4j
@Singleton
@Api(value = "Revolver Gateway", description = "Revolver api gateway endpoints")
public class RevolverRequestResource {

    private final ObjectMapper jsonObjectMapper;

    private final ObjectMapper msgPackObjectMapper;

    private final PersistenceProvider persistenceProvider;

    private final InlineCallbackHandler callbackHandler;

    private final MetricRegistry metrics;

    private static final Map<String, String> BAD_REQUEST_RESPONSE = Collections.singletonMap("message", "Bad Request");

    private static Map<String, String> SERVICE_UNAVAILABLE_RESPONSE = Collections.singletonMap("message", "Service Unavailable");

    private static final Map<String, String> DUPLICATE_REQUEST_RESPONSE = Collections.singletonMap("message", "Duplicate");

    public RevolverRequestResource(final ObjectMapper jsonObjectMapper,
                                   final ObjectMapper msgPackObjectMapper,
                                   final PersistenceProvider persistenceProvider, final InlineCallbackHandler callbackHandler,
                                   final MetricRegistry metrics) {
        this.jsonObjectMapper = jsonObjectMapper;
        this.msgPackObjectMapper = msgPackObjectMapper;
        this.persistenceProvider = persistenceProvider;
        this.callbackHandler = callbackHandler;
        this.metrics = metrics;
    }

    @GET
    @Path(value="/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver GET api endpoint")
    public Response get(@PathParam("service") final String service,
                        @PathParam("path") final String path, @Context final HttpHeaders headers, @Context final UriInfo uriInfo) throws Exception {
        Response response = processRequest(service, RevolverHttpApiConfig.RequestMethod.GET, path, headers, uriInfo, null);
        pushMetrics(response, service, path);
        return response;
    }

    @HEAD
    @Path(value="/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver HEAD api endpoint")
    public Response head(@PathParam("service") final String service,
                        @PathParam("path") final String path, @Context final HttpHeaders headers, @Context final UriInfo uriInfo) throws Exception {
        Response response = processRequest(service, RevolverHttpApiConfig.RequestMethod.HEAD, path, headers, uriInfo, null);
        pushMetrics(response, service, path);
        return response;
    }

    @POST
    @Path(value="/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver POST api endpoint")
    public Response post(@PathParam("service") final String service,
                        @PathParam("path") final String path, @Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws Exception {
        Response response = processRequest(service, RevolverHttpApiConfig.RequestMethod.POST, path, headers, uriInfo, body);
        pushMetrics(response, service, path);
        return response;
    }

    @PUT
    @Path(value="/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver PUT api endpoint")
    public Response put(@PathParam("service") final String service,
                         @PathParam("path") final String path, @Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws Exception {
        Response response =  processRequest(service, RevolverHttpApiConfig.RequestMethod.PUT, path, headers, uriInfo, body);
        pushMetrics(response, service, path);
        return response;
    }

    @DELETE
    @Path(value="/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver DELETE api endpoint")
    public Response delete(@PathParam("service") final String service,
                        @PathParam("path") final String path, @Context final HttpHeaders headers, @Context final UriInfo uriInfo) throws Exception {
        Response response =  processRequest(service, RevolverHttpApiConfig.RequestMethod.DELETE, path, headers, uriInfo, null);
        pushMetrics(response, service, path);
        return response;
    }

    @PATCH
    @Path(value="/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver PATCH api endpoint")
    public Response patch(@PathParam("service") final String service,
                        @PathParam("path") final String path, @Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws Exception {
        Response response = processRequest(service, RevolverHttpApiConfig.RequestMethod.PATCH, path, headers, uriInfo, body);
        pushMetrics(response, service, path);
        return response;
    }

    @OPTIONS
    @Path(value="/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver OPTIONS api endpoint")
    public Response options(@PathParam("service") final String service,
                          @PathParam("path") final String path, @Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws Exception {
        Response response = processRequest(service, RevolverHttpApiConfig.RequestMethod.OPTIONS, path, headers, uriInfo, body);
        pushMetrics(response, service, path);
        return response;
    }


    private Response processRequest(final String service, final RevolverHttpApiConfig.RequestMethod method, final String path,
                                    final HttpHeaders headers, final UriInfo uriInfo, final byte[] body) throws Exception {
        val apiMap = RevolverBundle.matchPath(service, path);
        if(apiMap == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                    ResponseTransformationUtil.transform(BAD_REQUEST_RESPONSE,
                            headers.getMediaType() != null ? headers.getMediaType().toString() : MediaType.APPLICATION_JSON,
                            jsonObjectMapper, msgPackObjectMapper)
            ).build();
        }
        String serviceKey = service +"." +apiMap.getApi().getApi();
        if(RevolverBundle.apiStatus.containsKey(serviceKey) && !RevolverBundle.apiStatus.get(serviceKey)) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(
                    ResponseTransformationUtil.transform(SERVICE_UNAVAILABLE_RESPONSE,
                            headers.getMediaType() != null ? headers.getMediaType().toString() : MediaType.APPLICATION_JSON,
                            jsonObjectMapper, msgPackObjectMapper)
            ).build();
        }
        val callMode = headers.getRequestHeaders().getFirst(RevolversHttpHeaders.CALL_MODE_HEADER);
        if(Strings.isNullOrEmpty(callMode)) {
          return executeInline(service, apiMap.getApi(), method, path, headers, uriInfo, body);
        }
        switch (callMode.toUpperCase()) {
            case RevolverHttpCommand.CALL_MODE_POLLING:
                return executeCommandAsync(service, apiMap.getApi(), method, path, headers, uriInfo, body, apiMap.getApi().isAsync(), callMode);
            case RevolverHttpCommand.CALL_MODE_CALLBACK:
                if(Strings.isNullOrEmpty(headers.getHeaderString(RevolversHttpHeaders.CALLBACK_URI_HEADER))) {
                    return Response.status(Response.Status.BAD_REQUEST).entity(
                            ResponseTransformationUtil.transform(BAD_REQUEST_RESPONSE,
                                    headers.getMediaType() != null ? headers.getMediaType().toString() : MediaType.APPLICATION_JSON,
                                    jsonObjectMapper, msgPackObjectMapper)
                    ).build();
                }
                return executeCommandAsync(service, apiMap.getApi(), method, path, headers, uriInfo, body, apiMap.getApi().isAsync(), callMode);
            case RevolverHttpCommand.CALL_MODE_CALLBACK_SYNC:
                if(Strings.isNullOrEmpty(headers.getHeaderString(RevolversHttpHeaders.CALLBACK_URI_HEADER))) {
                    return Response.status(Response.Status.BAD_REQUEST).entity(
                            ResponseTransformationUtil.transform(BAD_REQUEST_RESPONSE,
                                    headers.getMediaType() != null ? headers.getMediaType().toString() : MediaType.APPLICATION_JSON,
                                    jsonObjectMapper, msgPackObjectMapper)
                    ).build();
                }
                return executeCallbackSync(service, apiMap.getApi(), method, path, headers, uriInfo, body);
        }
        return Response.status(Response.Status.BAD_REQUEST).entity(
                ResponseTransformationUtil.transform(BAD_REQUEST_RESPONSE,
                        headers.getMediaType() != null ? headers.getMediaType().toString() : MediaType.APPLICATION_JSON,
                        jsonObjectMapper, msgPackObjectMapper)
        ).build();
    }

    private Response executeInline(final String service, final RevolverHttpApiConfig api, final RevolverHttpApiConfig.RequestMethod method,
                                   final String path, final HttpHeaders headers,
                                   final UriInfo uriInfo, final byte[] body) throws IOException, TimeoutException {
        val sanatizedHeaders = new MultivaluedHashMap<String, String>();
        headers.getRequestHeaders().forEach(sanatizedHeaders::put);
        cleanHeaders(sanatizedHeaders, api);
        val httpCommand = RevolverBundle.getHttpCommand(service, api.getApi());
        val response = httpCommand.execute(
                RevolverHttpRequest.builder()
                        .traceInfo(
                                TraceInfo.builder()
                                        .requestId(headers.getHeaderString(RevolversHttpHeaders.REQUEST_ID_HEADER))
                                        .transactionId(headers.getHeaderString(RevolversHttpHeaders.TXN_ID_HEADER))
                                        .timestamp(System.currentTimeMillis())
                                        .build())
                        .api(api.getApi())
                        .service(service)
                        .path(path)
                        .method(method)
                        .headers(sanatizedHeaders)
                        .queryParams(uriInfo.getQueryParameters())
                        .body(body)
                        .build()
        );
        return transform(headers, response, api.getApi(), path, method);
     }

    private Response transform(HttpHeaders headers, RevolverHttpResponse response, String api, String path, RevolverHttpApiConfig.RequestMethod method) throws IOException {
        val httpResponse = Response.status(response.getStatusCode());
        //Add all the headers except content type header
        if(response.getHeaders() != null ) {
            response.getHeaders().keySet().stream()
                    .filter( h -> !h.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE))
                    .filter(h -> !h.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
                    .forEach( h -> httpResponse.header(h, response.getHeaders().getFirst(h)));
        }
        httpResponse.header("X-REQUESTED-PATH", path);
        httpResponse.header("X-REQUESTED-METHOD", method);
        httpResponse.header("X-REQUESTED-API", api);
        final String responseMediaType = response.getHeaders() != null && Strings.isNullOrEmpty(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)) ? MediaType.TEXT_HTML : response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        final String requestMediaType = headers != null && Strings.isNullOrEmpty(headers.getHeaderString(HttpHeaders.ACCEPT)) ? null : headers.getHeaderString(HttpHeaders.ACCEPT);
        //If no no accept was specified in request; just send it as the same content type as response
        //Also send it as the content type as response content type if there requested content type is the same;
        if(Strings.isNullOrEmpty(requestMediaType) || requestMediaType.equals(responseMediaType)) {
            httpResponse.header(HttpHeaders.CONTENT_TYPE, responseMediaType);
            httpResponse.entity(response.getBody());
            return httpResponse.build();
        }
        Object responseData = null;
        if(responseMediaType.startsWith(MediaType.APPLICATION_JSON)) {
            final JsonNode jsonNode = jsonObjectMapper.readTree(response.getBody());
            if(jsonNode.isArray()) {
                responseData = jsonObjectMapper.convertValue(jsonNode, List.class);
            } else {
                responseData = jsonObjectMapper.convertValue(jsonNode, Map.class);
            }
        } else if(responseMediaType.startsWith(MsgPackMediaType.APPLICATION_MSGPACK)) {
            final JsonNode jsonNode = msgPackObjectMapper.readTree(response.getBody());
            if(jsonNode.isArray()) {
                responseData = msgPackObjectMapper.convertValue(jsonNode, List.class);
            } else {
                responseData = msgPackObjectMapper.convertValue(jsonNode, Map.class);
            }
        }
        if(responseData == null) {
            httpResponse.entity(response.getBody());
        } else {
            if(requestMediaType.startsWith(MediaType.APPLICATION_JSON)) {
                httpResponse.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                httpResponse.entity(jsonObjectMapper.writeValueAsBytes(responseData));
            } else if(requestMediaType.startsWith(MsgPackMediaType.APPLICATION_MSGPACK)) {
                httpResponse.header(HttpHeaders.CONTENT_TYPE, MsgPackMediaType.APPLICATION_MSGPACK);
                httpResponse.entity(msgPackObjectMapper.writeValueAsBytes(responseData));
            } else {
                httpResponse.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                httpResponse.entity(jsonObjectMapper.writeValueAsBytes(responseData));
            }
        }
        return httpResponse.build();
    }


    private void cleanHeaders(final MultivaluedMap<String, String> headers, RevolverHttpApiConfig apiConfig) {
        headers.remove(HttpHeaders.HOST);
        headers.remove(HttpHeaders.ACCEPT);
        headers.remove(HttpHeaders.ACCEPT_ENCODING);
        headers.putSingle(HttpHeaders.ACCEPT, apiConfig.getAcceptType());
        headers.putSingle(HttpHeaders.ACCEPT_ENCODING, apiConfig.getAcceptEncoding());
    }

    private Response executeCommandAsync(final String service, final RevolverHttpApiConfig api, final RevolverHttpApiConfig.RequestMethod method,
                                         final String path, final HttpHeaders headers,
                                         final UriInfo uriInfo, final byte[] body, final boolean isDownstreamAsync, final String callMode) throws Exception {
        val sanatizedHeaders = new MultivaluedHashMap<String, String>();
        headers.getRequestHeaders().forEach(sanatizedHeaders::put);
        cleanHeaders(sanatizedHeaders, api);
        val httpCommand = RevolverBundle.getHttpCommand(service, api.getApi());
        val requestId = headers.getHeaderString(RevolversHttpHeaders.REQUEST_ID_HEADER);
        val transactionId = headers.getHeaderString(RevolversHttpHeaders.TXN_ID_HEADER);
        val mailBoxId = headers.getHeaderString(RevolversHttpHeaders.MAILBOX_ID_HEADER);
        val mailBoxTtl = headers.getHeaderString(RevolversHttpHeaders.MAILBOX_TTL_HEADER) != null ?
                Integer.parseInt(headers.getHeaderString(RevolversHttpHeaders.MAILBOX_TTL_HEADER)) : -1;
        //Short circuit if it is a duplicate request
        if(persistenceProvider.exists(requestId)) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                    .entity(ResponseTransformationUtil.transform(DUPLICATE_REQUEST_RESPONSE,
                            headers.getMediaType() == null ? MediaType.APPLICATION_JSON : headers.getMediaType().toString(),
                            jsonObjectMapper, msgPackObjectMapper)).build();
        }
        persistenceProvider.saveRequest(requestId, mailBoxId,
                RevolverCallbackRequest.builder()
                        .api(api.getApi())
                        .mode(headers.getRequestHeaders().getFirst(RevolversHttpHeaders.CALL_MODE_HEADER))
                        .callbackUri(headers.getRequestHeaders().getFirst(RevolversHttpHeaders.CALLBACK_URI_HEADER))
                        .method(headers.getRequestHeaders().getFirst(RevolversHttpHeaders.CALLBACK_METHOD_HEADER))
                        .service(service)
                        .path(path)
                        .headers(headers.getRequestHeaders())
                        .queryParams(uriInfo.getQueryParameters())
                        .body(body)
                        .build(), mailBoxTtl
        );
        CompletableFuture<RevolverHttpResponse> response = httpCommand.executeAsync(
                RevolverHttpRequest.builder()
                        .traceInfo(
                                TraceInfo.builder()
                                        .requestId(requestId)
                                        .transactionId(transactionId)
                                        .timestamp(System.currentTimeMillis())
                                        .build())
                        .api(api.getApi())
                        .service(service)
                        .path(path)
                        .method(method)
                        .headers(sanatizedHeaders)
                        .queryParams(uriInfo.getQueryParameters())
                        .body(body)
                        .build()
        );
        //Async Downstream send accept on request path (Still circuit breaker will kick in. Keep circuit breaker aggressive)
        if(isDownstreamAsync) {
            val result = response.get();
            if(result.getStatusCode() == Response.Status.ACCEPTED.getStatusCode()) {
                persistenceProvider.setRequestState(requestId, RevolverRequestState.REQUESTED, mailBoxTtl);
            } else {
                persistenceProvider.setRequestState(requestId, RevolverRequestState.RESPONDED, mailBoxTtl);
                saveResponse(requestId, result, callMode, mailBoxTtl);
            }
            return transform(headers, result, api.getApi(), path, method);
        } else {
            response.thenAcceptAsync( result -> {
                try {
                    if(result.getStatusCode() == Response.Status.ACCEPTED.getStatusCode()) {
                        persistenceProvider.setRequestState(requestId, RevolverRequestState.REQUESTED, mailBoxTtl);
                    } else if(result.getStatusCode() == Response.Status.OK.getStatusCode()) {
                        persistenceProvider.setRequestState(requestId, RevolverRequestState.RESPONDED, mailBoxTtl);
                        saveResponse(requestId, result, callMode, mailBoxTtl);
                    } else {
                        persistenceProvider.setRequestState(requestId, RevolverRequestState.ERROR, mailBoxTtl);
                        saveResponse(requestId, result, callMode, mailBoxTtl);
                    }
                } catch (Exception e) {
                    log.error("Error setting request state for request id: {}", requestId, e);
                }
            });
            RevolverAckMessage revolverAckMessage = RevolverAckMessage.builder().requestId(requestId).acceptedAt(Instant.now().toEpochMilli()).build();
            return Response.accepted().entity(ResponseTransformationUtil.transform(revolverAckMessage,
                    headers.getMediaType() == null ? MediaType.APPLICATION_JSON : headers.getMediaType().toString(),
                    jsonObjectMapper, msgPackObjectMapper)).build();
        }
    }

    private Response executeCallbackSync(final String service, final RevolverHttpApiConfig api, final RevolverHttpApiConfig.RequestMethod method,
                                     final String path, final HttpHeaders headers,
                                     final UriInfo uriInfo, final byte[] body) throws Exception {
        val sanatizedHeaders = new MultivaluedHashMap<String, String>();
        headers.getRequestHeaders().forEach(sanatizedHeaders::put);
        cleanHeaders(sanatizedHeaders, api);
        val httpCommand = RevolverBundle.getHttpCommand(service, api.getApi());
        val requestId = headers.getHeaderString(RevolversHttpHeaders.REQUEST_ID_HEADER);
        val transactionId = headers.getHeaderString(RevolversHttpHeaders.TXN_ID_HEADER);
        val mailBoxId = headers.getHeaderString(RevolversHttpHeaders.MAILBOX_ID_HEADER);
        val mailBoxTtl = headers.getHeaderString(RevolversHttpHeaders.MAILBOX_TTL_HEADER) != null ?
                Integer.parseInt(headers.getHeaderString(RevolversHttpHeaders.MAILBOX_TTL_HEADER)) : -1;
        //Short circuit if it is a duplicate request
        if(persistenceProvider.exists(requestId)) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
                    .entity(ResponseTransformationUtil.transform(DUPLICATE_REQUEST_RESPONSE,
                            headers.getMediaType() == null ? MediaType.APPLICATION_JSON : headers.getMediaType().toString(),
                            jsonObjectMapper, msgPackObjectMapper)).build();
        }
        persistenceProvider.saveRequest(requestId, mailBoxId, RevolverCallbackRequest.builder()
                .api(api.getApi())
                .mode(headers.getRequestHeaders().getFirst(RevolversHttpHeaders.CALL_MODE_HEADER))
                .callbackUri(headers.getRequestHeaders().getFirst(RevolversHttpHeaders.CALLBACK_URI_HEADER))
                .method(headers.getRequestHeaders().getFirst(RevolversHttpHeaders.CALLBACK_METHOD_HEADER))
                .service(service)
                .path(path)
                .headers(headers.getRequestHeaders())
                .queryParams(uriInfo.getQueryParameters())
                .body(body)
                .build(), mailBoxTtl);
        CompletableFuture<RevolverHttpResponse> response = httpCommand.executeAsync(
                RevolverHttpRequest.builder()
                        .traceInfo(
                                TraceInfo.builder()
                                        .requestId(requestId)
                                        .transactionId(transactionId)
                                        .timestamp(System.currentTimeMillis())
                                        .build())
                        .api(api.getApi())
                        .service(service)
                        .path(path)
                        .method(method)
                        .headers(sanatizedHeaders)
                        .queryParams(uriInfo.getQueryParameters())
                        .body(body)
                        .build()
        );
        persistenceProvider.setRequestState(requestId, RevolverRequestState.REQUESTED, mailBoxTtl);
        val result = response.get();
        callbackHandler.handle(requestId, RevolverCallbackResponse.builder()
                .body(result.getBody())
                .headers(result.getHeaders())
                .statusCode(result.getStatusCode())
                .build());
        return transform(headers, result, api.getApi(), path, method);
    }

    private void saveResponse(String requestId, RevolverHttpResponse result, final String callMode, final int ttl) {
        try {
            val response = RevolverCallbackResponse.builder()
                    .body(result.getBody())
                    .headers(result.getHeaders())
                    .statusCode(result.getStatusCode())
                    .build();
            persistenceProvider.saveResponse(requestId, response, ttl);
            if(callMode != null && callMode.equals(RevolverHttpCommand.CALL_MODE_CALLBACK)) {
                callbackHandler.handle(requestId, response);
            }
        } catch (Exception e) {
            log.error("Error saving response!", e );
        }
    }

    private void pushMetrics(Response response, String service, String path){
        val apiMap = RevolverBundle.matchPath(service, path);
        String api = apiMap.getApi().getApi();
        metrics.meter(String.format("%s.%s.%s", service, api, response.getStatus()));
    }
}
