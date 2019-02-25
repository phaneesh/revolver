package io.dropwizard.revolver.spliting;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import lombok.val;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

/***
 Created by nitish.goyal on 25/02/19
 ***/
public class RevolverHttpApiSplitTest extends BaseRevolverTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9999);

    @Test
    public void testSimpleSplitHttpCommand() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(400)
                                                               .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v2/test")).willReturn(aResponse().withStatus(400)
                                                               .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/split")).willReturn(aResponse().withStatus(200)
                                                               .withHeader("Content-Type", "application/json")));

        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test_split");
        val request = RevolverHttpRequest.builder()
                .service("test")
                .api("test_split")
                .method(RevolverHttpApiConfig.RequestMethod.GET)
                .path("v1/split")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 400);

    }
}
