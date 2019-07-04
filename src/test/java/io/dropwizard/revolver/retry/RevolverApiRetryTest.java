package io.dropwizard.revolver.retry;

import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.RevolverExecutionException;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import lombok.val;
import org.junit.Test;

import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

/***
 Created by nitish.goyal on 25/02/19
 ***/
public class RevolverApiRetryTest extends BaseRevolverTest {

    @Test
    public void testRetryHttpCommand() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test_retry");
        val request = RevolverHttpRequest.builder().service("test").api("test_retry").method(RevolverHttpApiConfig.RequestMethod.GET).path("v1/test").build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);
    }


    @Test(expected = RevolverExecutionException.class)
    public void testFailedRetryHttpCommand() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(503).withHeader("Content-Type", "application/json")));
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test_retry");
        val request = RevolverHttpRequest.builder().service("test").api("test_retry").method(RevolverHttpApiConfig.RequestMethod.GET).path("v1/test").build();
        httpCommand.execute(request);
    }
}
