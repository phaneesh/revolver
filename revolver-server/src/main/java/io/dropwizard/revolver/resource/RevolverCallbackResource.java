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
import com.google.common.base.Strings;
import io.dropwizard.msgpack.MsgPackMediaType;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.HeaderUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * @author phaneesh
 */
@Path("/revolver")
@Slf4j
@Singleton
@Api(value = "RequestCallback", description = "Revolver gateway api for callbacks on mailbox requests")
public class RevolverCallbackResource {

    private static final String RESPONSE_CODE_HEADER = "X-RESPONSE-CODE";

    private final PersistenceProvider persistenceProvider;

    private final InlineCallbackHandler callbackHandler;

    public RevolverCallbackResource(PersistenceProvider persistenceProvider,
            InlineCallbackHandler callbackHandler) {
        this.persistenceProvider = persistenceProvider;
        this.callbackHandler = callbackHandler;
    }

    @Path("/v1/callback/{requestId}")
    @POST
    @Metered
    @ApiOperation(value = "Callback for updating responses for a given mailbox request")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK,
            MediaType.APPLICATION_XML})
    @Consumes({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK,
            MediaType.APPLICATION_XML})
    public Response handleCallback(@PathParam("requestId") String requestId,
            @HeaderParam(RESPONSE_CODE_HEADER) String responseCode,
            @Context HttpHeaders headers, byte[] responseBody) {
        long start = System.currentTimeMillis();
        try {
            val callbackRequest = persistenceProvider.request(requestId);
            log.debug("Callback request in handleCallback : " + callbackRequest);
            if (callbackRequest == null) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            val response = RevolverCallbackResponse.builder().body(responseBody)
                    .headers(headers.getRequestHeaders()).statusCode(
                            responseCode != null ? Integer.parseInt(responseCode)
                                    : Response.Status.OK.getStatusCode()).build();
            val mailboxTtl = HeaderUtil.getTTL(callbackRequest);
            persistenceProvider.saveResponse(requestId, response, mailboxTtl);
            if (callbackRequest.getMode() != null && (
                    callbackRequest.getMode().equals(RevolverHttpCommand.CALL_MODE_CALLBACK)
                            || callbackRequest.getMode()
                            .equals(RevolverHttpCommand.CALL_MODE_CALLBACK_SYNC)) && !Strings
                    .isNullOrEmpty(callbackRequest.getCallbackUri())) {
                callbackHandler.handle(requestId, response);
            }
            log.info(
                    "Callback processing for request id: {} with response size: {} bytes completed in {} ms",
                    requestId, responseBody.length, (System.currentTimeMillis() - start));
            return Response.accepted().build();
        } catch (Exception e) {
            log.error("Callback error", e);
            return Response.serverError().build();
        }
    }
}
