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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * @author phaneesh
 */
public class RevolverRequestResourceTest extends BaseRevolverTest {

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(new RevolverRequestResource(environment.getObjectMapper(),
                    RevolverBundle.msgPackObjectMapper, RevolverBundle.xmlObjectMapper, inMemoryPersistenceProvider))
            .build();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9999);

    @Test
    public void testGetRequest() {
        stubFor(get(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));
        assertEquals(resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .get().getStatus(), 200);
    }

    @Test
    public void testPostRequest() {
        stubFor(post(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));
        assertEquals(resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .post(null).getStatus(), 200);
    }

    @Test
    public void testPutRequest() {
        stubFor(put(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));
        assertEquals(resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .put(Entity.entity(Collections.singletonMap("test", "test"), MediaType.APPLICATION_JSON)).getStatus(), 200);
    }

    @Test
    public void testDeleteRequest() {
        stubFor(delete(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));
        assertEquals(resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .delete().getStatus(), 200);
    }

    @Test
    public void testHeadRequest() {
        stubFor(head(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));
        assertEquals(resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .head().getStatus(), 200);
    }

    @Test
    public void testPatchRequest() {
        stubFor(patch(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));
        assertEquals(resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .method("PATCH").getStatus(), 200);
    }

    @Test
    public void testOptionsRequest() {
        stubFor(options(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));
        assertEquals(resources.client().target("/apis/test/v1/test").request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID().toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID().toString())
                .options().getStatus(), 200);
    }


}