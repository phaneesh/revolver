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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.dropwizard.revolver.http.RevolverHttpCommand.CALL_MODE_POLLING;
import static org.junit.Assert.assertEquals;

import com.codahale.metrics.MetricRegistry;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.base.core.RevolverAckMessage;
import io.dropwizard.revolver.core.RevolverRequestStateResponse;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.testing.junit.ResourceTestRule;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * @author phaneesh
 */
public class RevolverMailboxResourceTest extends BaseRevolverTest {

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(
                    new RevolverRequestResource(environment.getObjectMapper(),
                            RevolverBundle.MSG_PACK_OBJECT_MAPPER, inMemoryPersistenceProvider,
                            callbackHandler, new MetricRegistry(), revolverConfig))
            .addResource(
                    new RevolverMailboxResource(inMemoryPersistenceProvider, environment.getObjectMapper(),
                            RevolverBundle.MSG_PACK_OBJECT_MAPPER,
                            Collections.unmodifiableMap(RevolverBundle.apiConfig)))
            .addResource(new RevolverCallbackResource(inMemoryPersistenceProvider, callbackHandler))
            .build();

    @Test
    public void shouldFetchResponseOnPollingWithoutMailboxAuthId() throws IOException {
        WireMock wireMockClient = new WireMock("localhost", wireMockRule.port());
        wireMockClient.register(WireMock
                .get(urlEqualTo("/v1/test"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json"))
        );

        Response response = submitPollingRequest(null);
        assertEquals(
                202, response.getStatus());
        String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), null);

        String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "RESPONDED");

        Response originalRequest = fetchMailboxRequest(revolverAckMessage.getRequestId(), null);
        assertEquals(200, originalRequest.getStatus());

        Response mailboxResponse = fetchMailboxResponse(revolverAckMessage.getRequestId(), null);
        assertEquals(200, mailboxResponse.getStatus());
    }


    @Test
    public void shouldFetchResponseOnPollingWithMailboxAuthId() throws IOException {
        Response response = submitPollingRequest("MAILBOX_123");
        assertEquals(202, response.getStatus());
        String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), "MAILBOX_123");

        String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "RESPONDED");

        Response originalRequest = fetchMailboxRequest(revolverAckMessage.getRequestId(), "MAILBOX_123");
        assertEquals(200, originalRequest.getStatus());

        Response mailboxResponse = fetchMailboxResponse(revolverAckMessage.getRequestId(), "MAILBOX_123");
        assertEquals(200, mailboxResponse.getStatus());
    }

    @Test
    public void shouldNotFetchRequestStateOnPollingWithoutMailboxAuthId() throws IOException {
        Response response = submitPollingRequest("MAILBOX_123");
        assertEquals(202, response.getStatus());
        String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), null);

        String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "UNKNOWN");
    }

    @Test
    public void shouldNotFetchRequestOnPollingWithoutMailboxAuthId() throws IOException {
        Response response = submitPollingRequest("MAILBOX_123");
        assertEquals(202, response.getStatus());
        String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), "MAILBOX_123");

        String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "RESPONDED");

        Response originalRequest = fetchMailboxRequest(revolverAckMessage.getRequestId(), null);
        assertEquals(500, originalRequest.getStatus());
    }

    @Test
    public void shouldNotFetchResponseOnPollingWithoutMailboxAuthId() throws IOException {
        Response response = submitPollingRequest("MAILBOX_123");
        assertEquals(202, response.getStatus());
        String json = response.readEntity(String.class);
        RevolverAckMessage revolverAckMessage = mapper.readValue(json, RevolverAckMessage.class);

        postCallback(revolverAckMessage.getRequestId());

        Response requestStatusResponse = fetchMailboxRequestStatus(revolverAckMessage.getRequestId(), "MAILBOX_123");

        String statusResponse = requestStatusResponse.readEntity(String.class);
        RevolverRequestStateResponse requestStateResponse = mapper
                .readValue(statusResponse, RevolverRequestStateResponse.class);
        assertEquals(requestStateResponse.getState(), "RESPONDED");

        Response mailboxResponse1 = fetchMailboxResponse(revolverAckMessage.getRequestId(), null);
        assertEquals(500, mailboxResponse1.getStatus());

        Response mailboxResponse2 = fetchMailboxResponse(revolverAckMessage.getRequestId(), "MAILBOX_1235");
        assertEquals(500, mailboxResponse2.getStatus());
    }

    public Response fetchMailboxResponse(String requestId, String mailboxAuthId) {
        return resources.client()
                .target("/revolver/v1/response/" + requestId).request()
                .header(RevolversHttpHeaders.MAILBOX_AUTH_ID_HEADER, mailboxAuthId)
                .get();
    }

    private Response fetchMailboxRequest(String requestId, String mailboxAuthId) {
        return resources.client()
                .target("/revolver/v1/request/" + requestId).request()
                .header(RevolversHttpHeaders.MAILBOX_AUTH_ID_HEADER, mailboxAuthId)
                .get();
    }

    private Response fetchMailboxRequestStatus(String requestId, String mailboxAuthId) {
        return resources.client()
                .target("/revolver/v1/request/status/" + requestId).request()
                .header(RevolversHttpHeaders.MAILBOX_AUTH_ID_HEADER, mailboxAuthId)
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
        assertEquals(202, callbackResponse.getStatus());
    }

    private Response submitPollingRequest(String mailboxAuthId) throws IOException {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"id\":1}")));
        return resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, CALL_MODE_POLLING)
                .header(RevolversHttpHeaders.MAILBOX_AUTH_ID_HEADER, mailboxAuthId)
                .get();

    }

}
