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
import com.github.tomakehurst.wiremock.client.WireMock;
import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.base.core.RevolverAckMessage;
import io.dropwizard.revolver.base.core.RevolverRequestStateResponse;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.dropwizard.revolver.http.RevolverHttpCommand.CALL_MODE_POLLING;
import static org.junit.Assert.assertEquals;

/**
 * @author phaneesh
 */
public class RevolverMailboxResourceTest extends BaseRevolverTest {

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(
                    new RevolverRequestResource(environment.getObjectMapper(),
                            RevolverBundle.msgPackObjectMapper, inMemoryPersistenceProvider,
                            callbackHandler, new MetricRegistry(), revolverConfig))
            .addResource(
                    new RevolverMailboxResource(inMemoryPersistenceProvider, environment.getObjectMapper(),
                            RevolverBundle.msgPackObjectMapper, Collections.unmodifiableMap(RevolverBundle.apiConfig)))
            .addResource(new RevolverCallbackResource(inMemoryPersistenceProvider, callbackHandler))
            .build();

    @Test
    public void shouldFetchResponseOnPollingWithoutMailboxId() throws IOException {
        WireMock wireMockClient = new WireMock("localhost", wireMockRule.port());
        wireMockClient.register(WireMock
                .get(urlEqualTo("/v1/test"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json"))
        );

        Response response = submitPollingRequest(null);
        assertEquals(
                response.getStatus(), 202);
        final String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), null);

        final String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "RESPONDED");

        Response originalRequest = fetchMailboxRequest(revolverAckMessage.getRequestId(), null);
        assertEquals(originalRequest.getStatus(), 200);

        Response mailboxResponse = fetchMailboxResponse(revolverAckMessage.getRequestId(), null);
        assertEquals(mailboxResponse.getStatus(), 200);
    }


    @Test
    public void shouldFetchResponseOnPollingWithMailboxId() throws IOException {
        Response response = submitPollingRequest("MAILBOX_123");
        assertEquals(
                response.getStatus(), 202);
        final String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), "MAILBOX_123");

        final String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "RESPONDED");

        Response originalRequest = fetchMailboxRequest(revolverAckMessage.getRequestId(), "MAILBOX_123");
        assertEquals(originalRequest.getStatus(), 200);

        Response mailboxResponse = fetchMailboxResponse(revolverAckMessage.getRequestId(), "MAILBOX_123");
        assertEquals(mailboxResponse.getStatus(), 200);
    }

    @Test
    public void shouldNotFetchRequestStateOnPollingWithoutMailboxId() throws IOException {
        Response response = submitPollingRequest("MAILBOX_123");
        assertEquals(
                response.getStatus(), 202);
        final String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), null);

        final String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "UNKNOWN");
    }

    @Test
    public void shouldNotFetchRequestOnPollingWithoutMailboxId() throws IOException {
        Response response = submitPollingRequest("MAILBOX_123");
        assertEquals(
                response.getStatus(), 202);
        final String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), "MAILBOX_123");

        final String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "RESPONDED");

        Response originalRequest = fetchMailboxRequest(revolverAckMessage.getRequestId(), null);
        assertEquals(originalRequest.getStatus(), 500);
    }

    @Test
    public void shouldNotFetchResponseOnPollingWithoutMailboxId() throws IOException {
        Response response = submitPollingRequest("MAILBOX_123");
        assertEquals(
                response.getStatus(), 202);
        final String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), "MAILBOX_123");

        final String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "RESPONDED");

        Response mailboxResponse1 = fetchMailboxResponse(revolverAckMessage.getRequestId(), null);
        assertEquals(mailboxResponse1.getStatus(), 500);

        Response mailboxResponse2 = fetchMailboxResponse(revolverAckMessage.getRequestId(), "MAILBOX_1235");
        assertEquals(mailboxResponse2.getStatus(), 500);
    }

    public Response fetchMailboxResponse(String requestId, String mailboxId) {
        return resources.client()
                .target("/revolver/v1/response/" + requestId).request()
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, mailboxId)
                .get();
    }

    private Response fetchMailboxRequest(String requestId, String mailboxId) {
        return resources.client()
                .target("/revolver/v1/request/" + requestId).request()
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, mailboxId)
                .get();
    }

    private Response fetchMailboxRequestStatus(String requestId, String mailboxId) {
        return resources.client()
                .target("/revolver/v1/request/status/" + requestId).request()
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, mailboxId)
                .get();
    }

    private void postCallback(String requestId) {
        Response callbackResponse = resources.client()
                .target("/revolver/v1/callback/" + requestId)
                .request()
                .header(RevolversHttpHeaders.CALLBACK_RESPONSE_CODE, 200)
                .header("Content-Type", "application/json")
                .post(Entity
                        .entity(Collections.singletonMap("test", "test"),
                                MediaType.APPLICATION_JSON));
        assertEquals(callbackResponse.getStatus(), 202);
    }

    private Response submitPollingRequest(String mailboxId) throws IOException {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"id\":1}")));
        return resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, CALL_MODE_POLLING)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, mailboxId)
                .get();

    }

}
