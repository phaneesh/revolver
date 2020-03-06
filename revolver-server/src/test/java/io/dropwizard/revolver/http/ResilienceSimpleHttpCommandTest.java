package io.dropwizard.revolver.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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

}
