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

package io.dropwizard.revolver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import io.dropwizard.Configuration;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.jetty.MutableServletContextHandler;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.core.config.ClientConfig;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.core.config.InMemoryMailBoxConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RuntimeConfig;
import io.dropwizard.revolver.core.config.ThreadPoolGroupConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.core.config.resilience.ResilienceCommandConfig;
import io.dropwizard.revolver.core.config.sentinel.SentinelCommandConfig;
import io.dropwizard.revolver.core.config.sentinel.SentinelFlowControlConfig;
import io.dropwizard.revolver.core.config.sentinel.SentinelGrade;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.discovery.RevolverServiceResolver;
import io.dropwizard.revolver.discovery.ServiceResolverConfig;
import io.dropwizard.revolver.discovery.model.SimpleEndpointSpec;
import io.dropwizard.revolver.handler.ConfigSource;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig.RequestMethod;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.config.RevolverHttpsServiceConfig;
import io.dropwizard.revolver.optimizer.OptimizerMetricsCache;
import io.dropwizard.revolver.optimizer.OptimizerMetricsCollector;
import io.dropwizard.revolver.optimizer.RevolverConfigUpdater;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import io.dropwizard.revolver.retry.RevolverApiRetryConfig;
import io.dropwizard.revolver.splitting.PathExpressionSplitConfig;
import io.dropwizard.revolver.splitting.RevolverHttpApiSplitConfig;
import io.dropwizard.revolver.splitting.RevolverHttpServiceSplitConfig;
import io.dropwizard.revolver.splitting.RevolverSplitServiceConfig;
import io.dropwizard.revolver.splitting.SplitConfig;
import io.dropwizard.revolver.splitting.SplitStrategy;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Before;
import org.junit.Rule;

/**
 * @author phaneesh
 */
@Slf4j
public class BaseRevolverTest {

    protected static final Environment environment = mock(Environment.class);
    protected static final ObjectMapper mapper = new ObjectMapper();
    protected static final InMemoryPersistenceProvider inMemoryPersistenceProvider = new InMemoryPersistenceProvider();
    protected static RevolverConfig revolverConfig;
    protected static OptimizerConfig optimizerConfig;
    protected static InlineCallbackHandler callbackHandler;

    private static RevolverExecutorType revolverExecutorType = RevolverExecutorType.RESILIENCE;

    static {
        when(environment.getObjectMapper()).thenReturn(mapper);

        val simpleEndpoint = new SimpleEndpointSpec();
        simpleEndpoint.setHost("localhost");
        simpleEndpoint.setPort(9999);

        val securedEndpoint = new SimpleEndpointSpec();
        securedEndpoint.setHost("localhost");
        securedEndpoint.setPort(9933);

        ThreadPoolConfig threadPoolConfig = ThreadPoolConfig.builder().concurrency(2)
                .dynamicRequestQueueSize(2).threadPoolName("test").timeout(100).build();

        SplitConfig splitConfigv1 = SplitConfig.builder().path("/v1/test").wrr(0.6).build();
        SplitConfig splitConfigv2 = SplitConfig.builder().path("/v2/test").wrr(0.4).build();
        SplitConfig splitConfigv4 = SplitConfig.builder().path("/v4/test").wrr(1).build();
        SplitConfig serviceSplitConfig1 = SplitConfig.builder().service("s1").wrr(0.9).build();
        SplitConfig serviceSplitConfig2 = SplitConfig.builder().service("s2").wrr(0.1).build();
        List<SplitConfig> splitConfigs = Lists.newArrayList(splitConfigv1, splitConfigv2);

        PathExpressionSplitConfig pathExpressionSplitConfig1 = PathExpressionSplitConfig.builder()
                .expression("(.*)").order(0).path("/v1/test").build();
        PathExpressionSplitConfig pathExpressionSplitConfig2 = PathExpressionSplitConfig.builder()
                .expression("(a-z)(0-9)(.*)").order(2).path("/v2/test").build();
        RevolverHttpApiSplitConfig revolverHttpPathExpressionConfig = RevolverHttpApiSplitConfig
                .builder().enabled(true).splitStrategy(SplitStrategy.PATH_EXPRESSION)
                .pathExpressionSplitConfigs(
                        Lists.newArrayList(pathExpressionSplitConfig1, pathExpressionSplitConfig2))
                .build();

        RevolverSplitServiceConfig s1 = RevolverSplitServiceConfig.builder().name("s1")
                .endpoint(simpleEndpoint).build();
        RevolverSplitServiceConfig s2 = RevolverSplitServiceConfig.builder().name("s2")
                .endpoint(securedEndpoint).build();

        optimizerConfig = OptimizerUtils.getDefaultOptimizerConfig();

        revolverConfig = RevolverConfig.builder().mailBox(InMemoryMailBoxConfig.builder().build())
                .serviceResolverConfig(
                        ServiceResolverConfig.builder().namespace("test").useCurator(false)
                                .zkConnectionString("localhost:2181").build())
                .clientConfig(ClientConfig.builder().clientName("test-client").build())
                .global(new RuntimeConfig()).optimizerConfig(optimizerConfig).service(
                        RevolverHttpServiceConfig.builder().authEnabled(false).connectionPoolSize(1)
                                .secured(false).enpoint(simpleEndpoint).service("test").type("http")
                                .threadPoolGroupConfig(ThreadPoolGroupConfig.builder()
                                        .threadPools(Lists.newArrayList(threadPoolConfig)).build())
                                .serviceSplitConfig(RevolverHttpServiceSplitConfig.builder()
                                        .configs(Lists.newArrayList(s1, s2)).build())
                                .sentinelCommandConfig(SentinelCommandConfig.builder()
                                        .flowControlConfig(SentinelFlowControlConfig.builder()
                                                .concurrency(1)
                                                .grade(SentinelGrade.FLOW_GRADE_THREAD)
                                                .poolName("test").build())
                                        .build())
                                .revolverExecutorType(revolverExecutorType)
                                .api(RevolverHttpApiConfig.configBuilder()
                                        .api("test")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/test")
                                        .runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(2000).build()).build())
                                        .sentinelCommandConfig(SentinelCommandConfig.builder()
                                                .flowControlConfig(SentinelFlowControlConfig.builder()
                                                        .concurrency(1)
                                                        .grade(SentinelGrade.FLOW_GRADE_THREAD)
                                                        .poolName("test-test").build())
                                                .build())
                                        .build())
                                .api(RevolverHttpApiConfig.configBuilder()
                                        .api("resilience-test")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/resilience-test")
                                        .runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(10000).build()).build())
                                        .resilienceCommandConfig(ResilienceCommandConfig.builder().build())
                                        .revolverExecutorType(revolverExecutorType)
                                        .build())
                                .api(RevolverHttpApiConfig.configBuilder()
                                        .api("test_path_expression")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/test_path_expression")
                                        .splitConfig(revolverHttpPathExpressionConfig)
                                        .runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(180000).build()).build())
                                        .sentinelCommandConfig(SentinelCommandConfig.builder()
                                                .flowControlConfig(SentinelFlowControlConfig.builder()
                                                        .poolName("test").build())
                                                .build())
                                        .build())
                                .api(RevolverHttpApiConfig.configBuilder().api("test_multi")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/test/{operation}")
                                        .runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(2000).build()).build())
                                        .sentinelCommandConfig(SentinelCommandConfig.builder()
                                                .flowControlConfig(SentinelFlowControlConfig.builder().concurrency(1)
                                                        .grade(SentinelGrade.FLOW_GRADE_THREAD)
                                                        .poolName("test-test_multi")
                                                        .build())
                                                .build())
                                        .build())
                                .api(RevolverHttpApiConfig.configBuilder().api("test_split")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/split").splitConfig(
                                                RevolverHttpApiSplitConfig.builder().enabled(true)
                                                        .splitStrategy(SplitStrategy.PATH)
                                                        .splits(splitConfigs).build()).runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(20000).build()).build())
                                        .build())
                                .api(RevolverHttpApiConfig.configBuilder().api("test_single_split")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/single_split").splitConfig(
                                                RevolverHttpApiSplitConfig.builder().enabled(true)
                                                        .splits(Lists.newArrayList(splitConfigv4))
                                                        .splitStrategy(SplitStrategy.PATH).build())
                                        .runtime(HystrixCommandConfig.builder().threadPool(
                                                ThreadPoolConfig.builder().concurrency(1)
                                                        .timeout(20000).build()).build()).build())
                                .api(RevolverHttpApiConfig.configBuilder().api("test_service_split")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/test_service_split").splitConfig(
                                                RevolverHttpApiSplitConfig.builder().enabled(true)
                                                        .splits(Lists
                                                                .newArrayList(serviceSplitConfig1,
                                                                        serviceSplitConfig2))
                                                        .splitStrategy(SplitStrategy.SERVICE)
                                                        .build()).runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(30000).build()).build())
                                        .build())
                                .api(RevolverHttpApiConfig.configBuilder().api("test_retry")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .retryConfig(RevolverApiRetryConfig.builder().enabled(true)
                                                .build()).path("{version}/test").runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(20000).build()).build())
                                        .build()).api(RevolverHttpApiConfig.configBuilder()
                                .api("test_group_thread_pool")
                                .method(RequestMethod.GET)
                                .method(RequestMethod.POST)
                                .method(RequestMethod.DELETE)
                                .method(RequestMethod.PATCH)
                                .method(RequestMethod.PUT)
                                .method(RequestMethod.HEAD)
                                .method(RequestMethod.OPTIONS)
                                .path("{version}/test/{operation}").runtime(
                                        HystrixCommandConfig.builder().threadPool(
                                                ThreadPoolConfig.builder().concurrency(1)
                                                        .timeout(2000).build()).build()).build())
                                .build()).service(
                        RevolverHttpServiceConfig.builder().authEnabled(false).connectionPoolSize(1)
                                .secured(false).enpoint(simpleEndpoint).service("test-without-pool")
                                .type("http").serviceSplitConfig(
                                RevolverHttpServiceSplitConfig.builder()
                                        .configs(Lists.newArrayList(s1, s2)).build())
                                .api(RevolverHttpApiConfig.configBuilder().api("test")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("test").runtime(HystrixCommandConfig.builder()
                                                .threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(2000).build()).build())
                                        .build()).build()).service(
                        RevolverHttpsServiceConfig.builder().authEnabled(false)
                                .connectionPoolSize(1).enpoint(securedEndpoint)
                                .service("test_secured").type("https")
                                .api(RevolverHttpApiConfig.configBuilder().api("test")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/test").runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(2000).build()).build())
                                        .build())
                                .api(RevolverHttpApiConfig.configBuilder().api("test_multi")
                                        .method(RequestMethod.GET)
                                        .method(RequestMethod.POST)
                                        .method(RequestMethod.DELETE)
                                        .method(RequestMethod.PATCH)
                                        .method(RequestMethod.PUT)
                                        .method(RequestMethod.HEAD)
                                        .method(RequestMethod.OPTIONS)
                                        .path("{version}/test/{operation}").runtime(
                                                HystrixCommandConfig.builder().threadPool(
                                                        ThreadPoolConfig.builder().concurrency(1)
                                                                .timeout(2000).build()).build())
                                        .build()).build()).build();

        RevolverBundle.serviceNameResolver = RevolverServiceResolver.builder()
                .objectMapper(environment.getObjectMapper()).build();
        RevolverBundle.loadServiceConfiguration(revolverConfig);

    }

    protected final HealthCheckRegistry healthChecks = mock(HealthCheckRegistry.class);
    protected final JerseyEnvironment jerseyEnvironment = mock(JerseyEnvironment.class);
    protected final LifecycleEnvironment lifecycleEnvironment = new LifecycleEnvironment();
    protected final Bootstrap<?> bootstrap = mock(Bootstrap.class);
    protected final Configuration configuration = mock(Configuration.class);
    protected final RevolverBundle<Configuration> bundle = new RevolverBundle<Configuration>() {

        @Override
        public RevolverConfig getRevolverConfig(Configuration configuration) {
            return revolverConfig;
        }

        @Override
        public String getRevolverConfigAttribute() {
            return "revolver";
        }

        @Override
        public CuratorFramework getCurator() {
            return null;
        }

        @Override
        public ConfigSource getConfigSource() {
            return null;
        }
    };
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9999, 9933);
    protected RevolverConfigUpdater revolverConfigUpdater;
    protected OptimizerMetricsCollector optimizerMetricsCollector;
    protected OptimizerMetricsCache optimizerMetricsCache;
    private MetricRegistry metricRegistry = new MetricRegistry();

    @Before
    public void setup()
            throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException, InterruptedException {
        when(jerseyEnvironment.getResourceConfig()).thenReturn(new DropwizardResourceConfig());
        when(environment.jersey()).thenReturn(jerseyEnvironment);
        when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
        when(environment.healthChecks()).thenReturn(healthChecks);
        when(bootstrap.getObjectMapper()).thenReturn(mapper);
        when(environment.metrics()).thenReturn(metricRegistry);
        when(environment.getApplicationContext()).thenReturn(new MutableServletContextHandler());

        bundle.initialize(bootstrap);

        bundle.run(configuration, environment);

        lifecycleEnvironment.getManagedObjects().forEach(object -> {
            try {
                object.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        callbackHandler = InlineCallbackHandler.builder()
                .persistenceProvider(inMemoryPersistenceProvider).revolverConfig(revolverConfig)
                .build();

        optimizerMetricsCache = OptimizerMetricsCache.builder()
                .optimizerMetricsCollectorConfig(optimizerConfig.getMetricsCollectorConfig())
                .build();
        optimizerMetricsCollector = OptimizerMetricsCollector.builder().metrics(metricRegistry)
                .optimizerMetricsCache(optimizerMetricsCache).optimizerConfig(optimizerConfig)
                .build();
        revolverConfigUpdater = RevolverConfigUpdater.builder()
                .optimizerMetricsCache(optimizerMetricsCache).revolverConfig(revolverConfig)
                .optimizerConfig(optimizerConfig).build();
    }
}
