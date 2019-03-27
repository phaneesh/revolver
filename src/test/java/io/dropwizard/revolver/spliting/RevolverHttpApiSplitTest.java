package io.dropwizard.revolver.spliting;

import com.google.common.collect.Lists;
import io.dropwizard.revolver.BaseRevolverTest;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.*;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.discovery.ServiceResolverConfig;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import io.dropwizard.revolver.splitting.RevolverHttpApiSplitConfig;
import io.dropwizard.revolver.splitting.SplitConfig;
import lombok.val;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

/***
 Created by nitish.goyal on 25/02/19
 ***/
public class RevolverHttpApiSplitTest extends BaseRevolverTest {

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

    @Test(expected = RuntimeException.class)
    public void testFalseSplitHttpCommand() throws TimeoutException {

        SplitConfig splitConfigv1 = SplitConfig.builder()
                .path("/v1/test")
                .wrr(0.6)
                .build();
        SplitConfig splitConfigv2 = SplitConfig.builder()
                .path("/v2/test")
                .wrr(0.4)
                .build();
        SplitConfig splitConfigv3 = SplitConfig.builder()
                .path("/v3/test")
                .wrr(0.4)
                .build();

        List<SplitConfig> falseSplitConfigs = Lists.newArrayList(splitConfigv1, splitConfigv2, splitConfigv3);
        RevolverConfig.builder()
                .mailBox(InMemoryMailBoxConfig.builder()
                                 .build())
                .serviceResolverConfig(ServiceResolverConfig.builder()
                                               .namespace("test")
                                               .useCurator(false)
                                               .zkConnectionString("localhost:2181")
                                               .build())
                .clientConfig(ClientConfig.builder()
                                      .clientName("test-client")
                                      .build())
                .global(new RuntimeConfig())
                .service(RevolverHttpServiceConfig.builder()
                                 .authEnabled(false)
                                 .connectionPoolSize(1)
                                 .secured(false)
                                 .service("test")
                                 .type("http")
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test_false_split")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test_false_split")
                                              .splitConfig(RevolverHttpApiSplitConfig.builder()
                                                                   .enabled(true)
                                                                   .splits(falseSplitConfigs)
                                                                   .build())
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(20000)
                                                                                   .build())
                                                               .build())
                                              .build())

                                 .build());
        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test_false_split");
        val request = RevolverHttpRequest.builder()
                .service("test")
                .api("test_false_split")
                .method(RevolverHttpApiConfig.RequestMethod.GET)
                .path("v1/test_false_split")
                .build();
        httpCommand.execute(request);
    }

    @Test
    public void testSingleSplitHttpCommand() throws TimeoutException {
        stubFor(get(urlEqualTo("/v4/test")).willReturn(aResponse().withStatus(400)
                                                                            .withHeader("Content-Type", "application/json")));

        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test_single_split");
        val request = RevolverHttpRequest.builder()
                .service("test")
                .api("test_single_split")
                .method(RevolverHttpApiConfig.RequestMethod.GET)
                .path("/v1/test_single_split")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 400);

    }

    @Test
    public void testSimpleSplitServiceHttpCommand() throws TimeoutException {
        stubFor(get(urlEqualTo("/v1/test_service_split")).willReturn(aResponse().withStatus(200)
                                                               .withHeader("Content-Type", "application/json")));

        RevolverHttpCommand httpCommand = RevolverBundle.getHttpCommand("test", "test_service_split");
        val request = RevolverHttpRequest.builder()
                .service("test")
                .api("test_service_split")
                .method(RevolverHttpApiConfig.RequestMethod.GET)
                .path("v1/test_service_split")
                .build();
        val response = httpCommand.execute(request);
        assertEquals(response.getStatusCode(), 200);

    }

}
