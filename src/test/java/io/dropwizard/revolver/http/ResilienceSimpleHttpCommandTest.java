package io.dropwizard.revolver.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.options;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;

import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import java.util.concurrent.TimeoutException;
import lombok.val;
import org.junit.Test;

public class ResilienceSimpleHttpCommandTest extends BaseRevolverTest {

    @Test
    public void testSimpleGetHttpCommand() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/resilience-test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "resilience-test");
        val request = RevolverHttpRequest.builder().service("test").api("resilience-test")
                .method(RevolverHttpApiConfig.RequestMethod.GET).path("v1/resilience-test")
                .revolverExecutorType(RevolverExecutorType.RESILIENCE).build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimpleGetHttpCommandWithWrongPath() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.GET).path("v1/test_invalid").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimpleGetHttpCommandWithMultiplePathSegment() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.GET).path("v1/test/multi").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimpleGetHttpCommandWithMultiplePathSegmentWithWrongPath()
            throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.GET).path("v1/test/multi_invalid")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimplePostHttpCommand() throws TimeoutException {
        stubFor(post(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.POST).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimplePostHttpCommandWithWithWrongPath() throws TimeoutException {
        stubFor(post(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.POST).path("v1/test_invalid").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimplePostHttpCommandWithMultiplePathSegment() throws TimeoutException {
        stubFor(post(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.POST).path("v1/test/multi").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimplePostHttpCommandWithMultiplePathSegmentWithWrongPath()
            throws TimeoutException {
        stubFor(post(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.POST).path("v1/test/multi_invalid")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimplePutHttpCommand() throws TimeoutException {
        stubFor(put(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.PUT).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimplePutHttpCommandWithWrongPath() throws TimeoutException {
        stubFor(put(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.PUT).path("v1/test_invalid").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimplePutHttpCommandWithMultiplePathSegment() throws TimeoutException {
        stubFor(put(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.PUT).path("v1/test/multi").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimplePutHttpCommandWithMultiplePathSegmentWithWrongPath()
            throws TimeoutException {
        stubFor(put(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.PUT).path("v1/test/multi_invalid")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }


    @Test
    public void testSimpleDeleteHttpCommand() throws TimeoutException {
        stubFor(delete(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.DELETE).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimpleDeleteHttpCommandWithWrongPath() throws TimeoutException {
        stubFor(delete(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.DELETE).path("v1/test_invalid").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimpleDeleteHttpCommandWithMultiplePathSegment() throws TimeoutException {
        stubFor(delete(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.DELETE).path("v1/test/multi").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimpleDeleteHttpCommandWithMultiplePathSegmentWithWrongPath()
            throws TimeoutException {
        stubFor(delete(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.DELETE).path("v1/test/multi_invalid")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimpleHeadHttpCommand() throws TimeoutException {
        stubFor(head(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.HEAD).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimpleHeadHttpCommandWithWrongPath() throws TimeoutException {
        stubFor(head(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.HEAD).path("v1/test_invalid").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimpleHeadHttpCommandWithMultiplePathSegment() throws TimeoutException {
        stubFor(head(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.HEAD).path("v1/test/multi").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimpleHeadHttpCommandWithMultiplePathSegmentWithWrongPath()
            throws TimeoutException {
        stubFor(head(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.HEAD).path("v1/test/multi_invalid")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimplePatchHttpCommand() throws TimeoutException {
        stubFor(patch(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.PATCH).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimplePatchHttpCommandWithWrongPath() throws TimeoutException {
        stubFor(patch(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.PATCH).path("v1/test_invalid").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimplePatchHttpCommandWithMultiplePathSegment() throws TimeoutException {
        stubFor(patch(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.PATCH).path("v1/test/multi").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimplePatchHttpCommandWithMultiplePathSegmentWithWrongPath()
            throws TimeoutException {
        stubFor(patch(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.PATCH).path("v1/test/multi_invalid")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimpleOptionsHttpCommand() throws TimeoutException {
        stubFor(options(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.OPTIONS).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimpleOptionsHttpCommandWithWrongPath() throws TimeoutException {
        stubFor(options(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.OPTIONS).path("v1/test_invalid")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSimpleOptionHttpCommandWithMultiplePathSegment() throws TimeoutException {
        stubFor(options(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.OPTIONS).path("v1/test/multi").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testSimpleOptionsHttpCommandWithMultiplePathSegmentWithWrongPath()
            throws TimeoutException {
        stubFor(patch(urlEqualTo("/v1/test/multi")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.OPTIONS).path("v1/test/multi_invalid")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 404);
    }

    @Test
    public void testSharedPoolHttpCommand() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test");
        httpCommand.getServiceConfiguration().getApis().forEach(a -> a.setSharedPool(true));
        val request = RevolverHttpRequest.builder().service("test").api("test")
                .method(RevolverHttpApiConfig.RequestMethod.GET).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void testGroupPoolAtApiLevelHttpCommand() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle
                .getHttpCommand("test", "test_group_thread_pool");
        val request = RevolverHttpRequest.builder().service("test").api("test_group_thread_pool")
                .method(RevolverHttpApiConfig.RequestMethod.GET).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }

}
