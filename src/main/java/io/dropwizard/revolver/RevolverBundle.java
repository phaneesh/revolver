/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dropwizard.revolver;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.collections.CollectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.strategy.HystrixPlugins;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.msgpack.MsgPackBundle;
import io.dropwizard.revolver.aeroapike.AerospikeConnectionManager;
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.core.RevolverExecutionException;
import io.dropwizard.revolver.core.config.AerospikeMailBoxConfig;
import io.dropwizard.revolver.core.config.InMemoryMailBoxConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.ServiceDiscoveryConfig;
import io.dropwizard.revolver.core.config.hystrix.HystrixUtil;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.core.resilience.ResilienceHttpContext;
import io.dropwizard.revolver.core.resilience.ResilienceUtil;
import io.dropwizard.revolver.core.sentinel.SentinelUtil;
import io.dropwizard.revolver.discovery.RevolverServiceResolver;
import io.dropwizard.revolver.discovery.model.RangerEndpointSpec;
import io.dropwizard.revolver.discovery.model.SimpleEndpointSpec;
import io.dropwizard.revolver.exception.RevolverExceptionMapper;
import io.dropwizard.revolver.exception.TimeoutExceptionMapper;
import io.dropwizard.revolver.filters.RevolverRequestFilter;
import io.dropwizard.revolver.handler.ConfigSource;
import io.dropwizard.revolver.handler.DynamicConfigHandler;
import io.dropwizard.revolver.http.RevolverHttpClientFactory;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolverHttpContext;
import io.dropwizard.revolver.http.auth.BasicAuthConfig;
import io.dropwizard.revolver.http.auth.TokenAuthConfig;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.config.RevolverHttpsServiceConfig;
import io.dropwizard.revolver.http.model.ApiPathMap;
import io.dropwizard.revolver.optimizer.OptimizerMetricsCache;
import io.dropwizard.revolver.optimizer.OptimizerMetricsCollector;
import io.dropwizard.revolver.optimizer.RevolverConfigUpdater;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConfigUpdaterConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerMetricsCollectorConfig;
import io.dropwizard.revolver.persistence.AeroSpikePersistenceProvider;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.resource.RevolverApiManageResource;
import io.dropwizard.revolver.resource.RevolverCallbackResource;
import io.dropwizard.revolver.resource.RevolverConfigResource;
import io.dropwizard.revolver.resource.RevolverMailboxResource;
import io.dropwizard.revolver.resource.RevolverMetadataResource;
import io.dropwizard.revolver.resource.RevolverRequestResource;
import io.dropwizard.revolver.splitting.PathExpressionSplitConfig;
import io.dropwizard.revolver.splitting.SplitConfig;
import io.dropwizard.riemann.RiemannBundle;
import io.dropwizard.riemann.RiemannConfig;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * @author phaneesh
 */
@Slf4j
public abstract class RevolverBundle<T extends Configuration> implements ConfiguredBundle<T> {

    public static final ObjectMapper MSG_PACK_OBJECT_MAPPER = new ObjectMapper(
            new MessagePackFactory());
    public static final Map<String, Boolean> apiStatus = new ConcurrentHashMap<>();

    public static RevolverServiceResolver serviceNameResolver = null;
    private static Map<String, RevolverHttpServiceConfig> serviceConfig = new ConcurrentHashMap<>();
    private static Map<String, RevolverHttpApiConfig> apiConfig = new ConcurrentHashMap<>();
    private static MultivaluedMap<String, ApiPathMap> serviceToPathMap = new MultivaluedHashMap<>();
    private static Map<String, Integer> serviceConnectionPoolMap = new ConcurrentHashMap<>();

    private static RevolverConfig revolverConfig;
    private static ResilienceHttpContext resilienceHttpContext = new ResilienceHttpContext();

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        //Reset everything before configuration
        HystrixPlugins.reset();
        registerTypes(bootstrap);
        bootstrap.addBundle(new MsgPackBundle());
        bootstrap.addBundle(
                new AssetsBundle("/revolver/dashboard/", "/revolver/dashboard/", "index.html"));
        bootstrap.addBundle(new RiemannBundle<Configuration>() {
            @Override
            public RiemannConfig getRiemannConfiguration(Configuration configuration) {
                if (configuration instanceof RevolverConfig) {
                    return ((RevolverConfig) configuration).getRiemann();
                }
                return null;
            }
        });
    }

    @Override
    public void run(T configuration, Environment environment) {
        //Add metrics publisher
        HystrixCodaHaleMetricsPublisher metricsPublisher = new HystrixCodaHaleMetricsPublisher(
                environment.metrics());
        val metrics = environment.metrics();
        ScheduledExecutorService scheduledExecutorService = environment.lifecycle()
                .scheduledExecutorService("metrics-builder").build();

        initializeRevolver(configuration, environment);

        HystrixUtil.initializeHystrix(environment, metricsPublisher, revolverConfig);
        SentinelUtil.initializeSentinel(revolverConfig);
        ResilienceUtil.initializeResilience(revolverConfig, resilienceHttpContext, environment.metrics());

        initializeOptimizer(metrics, scheduledExecutorService);

        PersistenceProvider persistenceProvider = getPersistenceProvider(configuration,
                environment);
        InlineCallbackHandler callbackHandler = InlineCallbackHandler.builder()
                .persistenceProvider(persistenceProvider).revolverConfig(revolverConfig).build();

        registerResources(environment, metrics, persistenceProvider, callbackHandler);
        registerMappers(environment);
        registerFilters(environment);
    }


    public abstract RevolverConfig getRevolverConfig(T configuration);

    public abstract String getRevolverConfigAttribute();

    public abstract ConfigSource getConfigSource();

    public void onConfigChange(String configData) {
        log.info("Config changed! Override to propagate config changes to other bundles");
    }

    PersistenceProvider getPersistenceProvider(T configuration, Environment environment) {
        RevolverConfig revolverConfig = getRevolverConfig(configuration);
        //Default for avoiding no mailbox config NPE
        if (revolverConfig.getMailBox() == null) {
            return new InMemoryPersistenceProvider();
        }
        switch (revolverConfig.getMailBox().getType()) {
            case "in_memory":
                return new InMemoryPersistenceProvider();
            case "aerospike":
                AerospikeConnectionManager
                        .init((AerospikeMailBoxConfig) revolverConfig.getMailBox());
                return new AeroSpikePersistenceProvider(
                        (AerospikeMailBoxConfig) revolverConfig.getMailBox(),
                        environment.getObjectMapper());
        }
        throw new IllegalArgumentException("Invalid mailbox configuration");
    }

    public abstract CuratorFramework getCurator();


    public static ApiPathMap matchPath(String service, String path) {
        if (serviceToPathMap.containsKey(service)) {
            val apiMap = serviceToPathMap.get(service).stream()
                    .filter(api -> path.matches(api.getPath())).findFirst();
            return apiMap.orElse(null);
        } else {
            return null;
        }
    }

    public static RevolverHttpCommand getHttpCommand(String service, String api) {
        if (!serviceConfig.containsKey(service)) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST,
                    "No service spec defined for service: " + service);
        }
        String serviceKey = service + "." + api;
        if (!apiConfig.containsKey(serviceKey)) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST,
                    "No api spec defined for service: " + service);
        }
        RevolverHttpContext revolverContext = getRevolverContext(serviceKey);
        return RevolverHttpCommand.builder().apiConfiguration(apiConfig.get(serviceKey))
                .clientConfiguration(revolverConfig.getClientConfig())
                .runtimeConfig(revolverConfig.getGlobal())
                .revolverContext(revolverContext)
                .serviceConfiguration(serviceConfig.get(service)).build();
    }

    private static RevolverHttpContext getRevolverContext(String serviceKey) {
        RevolverHttpApiConfig revolverHttpApiConfig = apiConfig.get(serviceKey);
        RevolverExecutorType revolverExecutorType = revolverHttpApiConfig.getRevolverExecutorType();
        if (revolverExecutorType == null) {
            return new ResilienceHttpContext();
        }
        switch (revolverExecutorType) {
            case RESILIENCE:
                return resilienceHttpContext;
        }
        return new RevolverHttpContext();
    }

    public static RevolverServiceResolver getServiceNameResolver() {
        return serviceNameResolver;
    }

    public static void loadServiceConfiguration(RevolverConfig revolverConfig) {
        for (RevolverServiceConfig config : revolverConfig.getServices()) {
            String type = config.getType();
            switch (type) {
                case "http":
                    registerHttpCommand(config);
                    break;
                case "https":
                    registerHttpsCommand(config);
                    break;
                default:
                    log.warn("Unsupported Service type: " + type);

            }
        }
    }

    public static Map<String, RevolverHttpServiceConfig> getServiceConfig() {
        return serviceConfig;
    }

    public static void addHttpCommand(RevolverHttpServiceConfig config) {
        if (!serviceConfig.containsKey(config.getService())) {
            serviceConfig.put(config.getService(), config);
            registerCommand(config, config);
        }
    }


    private static void registerHttpsCommand(RevolverServiceConfig config) {
        RevolverHttpsServiceConfig httpsConfig = (RevolverHttpsServiceConfig) config;
        RevolverHttpServiceConfig revolverHttpServiceConfig = RevolverHttpServiceConfig.builder()
                .apis(httpsConfig.getApis()).auth(httpsConfig.getAuth())
                .authEnabled(httpsConfig.isAuthEnabled()).compression(httpsConfig.isCompression())
                .connectionKeepAliveInMillis(httpsConfig.getConnectionKeepAliveInMillis())
                .connectionPoolSize(httpsConfig.getConnectionPoolSize())
                .enpoint(httpsConfig.getEndpoint())
                .keystorePassword(httpsConfig.getKeystorePassword())
                .keyStorePath(httpsConfig.getKeyStorePath()).secured(true)
                .service(httpsConfig.getService()).trackingHeaders(httpsConfig.isTrackingHeaders())
                .type(httpsConfig.getType()).build();
        registerCommand(config, revolverHttpServiceConfig);
    }

    private static void registerCommand(RevolverServiceConfig config,
            RevolverHttpServiceConfig revolverHttpServiceConfig) {

        if (config instanceof RevolverHttpServiceConfig) {

            setTotalConcurrencyForService(config);
            setApiSettings(config);

            generateApiConfigMap((RevolverHttpServiceConfig) config);
            serviceNameResolver.register(revolverHttpServiceConfig.getEndpoint());
        }
    }

    private static void setApiSettings(RevolverServiceConfig config) {
        ((RevolverHttpServiceConfig) config).getApis().forEach(a -> {
            String key = config.getService() + "." + a.getApi();
            apiStatus.put(key, true);
            apiConfig.put(key, a);
            if (a.getRuntime() != null && a.getRuntime().getThreadPool() != null) {
                if (a.getRuntime().getThreadPool().getInitialConcurrency() == 0) {
                    a.getRuntime().getThreadPool()
                            .setInitialConcurrency(a.getRuntime().getThreadPool().getConcurrency());
                }
            }
            if (null != a.getSplitConfig() && a.getSplitConfig().isEnabled()) {
                updateSplitConfig(a);
                if (CollectionUtils
                        .isNotEmpty(a.getSplitConfig().getPathExpressionSplitConfigs())) {

                    List<PathExpressionSplitConfig> sortedOnOrder = a.getSplitConfig()
                            .getPathExpressionSplitConfigs().stream()
                            .sorted(Comparator.comparing(PathExpressionSplitConfig::getOrder))
                            .collect(Collectors.toList());
                    a.getSplitConfig().setPathExpressionSplitConfigs(sortedOnOrder);
                }
            }
        });
    }

    private static void setTotalConcurrencyForService(RevolverServiceConfig config) {
        //Adjust connectionPool size to make sure we don't starve connections. Guard against misconfiguration
        //1. Add concurrency from thread pool groups
        //2. Add concurrency from apis which do not belong to any thread pool group
        int totalConcurrency = 0;
        if (config.getThreadPoolGroupConfig() != null) {
            totalConcurrency = config.getThreadPoolGroupConfig().getThreadPools().stream()
                    .mapToInt(ThreadPoolConfig::getConcurrency).sum();
            config.getThreadPoolGroupConfig().getThreadPools()
                    .forEach(a -> {
                        if (a.getInitialConcurrency() == 0) {
                            log.info("Initial Concurrency : {}, Thread Pool : {}", a.getInitialConcurrency(),
                                    a.getThreadPoolName());
                            a.setInitialConcurrency(a.getConcurrency());
                        }
                    });
        }
        totalConcurrency += ((RevolverHttpServiceConfig) config).getApis().stream()
                .filter(a -> Strings
                        .isNullOrEmpty(a.getRuntime().getThreadPool().getThreadPoolName()))
                .mapToInt(a -> a.getRuntime().getThreadPool().getConcurrency()).sum();

        ((RevolverHttpServiceConfig) config).setConnectionPoolSize(totalConcurrency);
    }

    private static void updateSplitConfig(RevolverHttpApiConfig apiConfig) {
        double from = 0.0;
        for (SplitConfig splitConfig : CollectionUtils
                .nullSafeList(apiConfig.getSplitConfig().getSplits())) {
            double wrr = splitConfig.getWrr();
            splitConfig.setFrom(from);
            from += wrr;
            splitConfig.setTo(from);
        }
        if (from > 1.0) {
            throw new RuntimeException("wrr of split api is exceeding weight of 1");
        }
    }

    private void initializeOptimizer(MetricRegistry metrics, ScheduledExecutorService scheduledExecutorService) {
        OptimizerConfig optimizerConfig = revolverConfig.getOptimizerConfig();
        if (optimizerConfig != null && optimizerConfig.isEnabled()) {
            OptimizerMetricsCache optimizerMetricsCache = OptimizerMetricsCache.builder().
                    optimizerMetricsCollectorConfig(optimizerConfig.getMetricsCollectorConfig())
                    .build();

            if (optimizerConfig.getConcurrencyConfig() != null && optimizerConfig
                    .getConcurrencyConfig().isEnabled()) {
                OptimizerMetricsCollector optimizerMetricsCollector = OptimizerMetricsCollector
                        .builder().metrics(metrics).optimizerMetricsCache(optimizerMetricsCache)
                        .optimizerConfig(optimizerConfig).build();
                scheduledExecutorService.scheduleAtFixedRate(optimizerMetricsCollector,
                        optimizerConfig.getInitialDelay(),
                        optimizerConfig.getMetricsCollectorConfig().getRepeatAfter(),
                        optimizerConfig.getMetricsCollectorConfig().getTimeUnit());
            }

            if (optimizerConfig.getTimeConfig() != null && optimizerConfig.getTimeConfig()
                    .isEnabled()) {
                RevolverConfigUpdater revolverConfigUpdater = RevolverConfigUpdater.builder()
                        .optimizerConfig(optimizerConfig)
                        .optimizerMetricsCache(optimizerMetricsCache).revolverConfig(revolverConfig)
                        .build();
                scheduledExecutorService.scheduleAtFixedRate(revolverConfigUpdater,
                        optimizerConfig.getInitialDelay(),
                        optimizerConfig.getConfigUpdaterConfig().getRepeatAfter(),
                        optimizerConfig.getConfigUpdaterConfig().getTimeUnit());
            }
        }
    }

    private void initializeRevolver(T configuration, Environment environment) {
        revolverConfig = getRevolverConfig(configuration);
        ServiceDiscoveryConfig serviceDiscoveryConfig = revolverConfig.getServiceDiscoveryConfig();
        if (serviceDiscoveryConfig == null) {
            log.info("ServiceDiscovery in null");
            serviceDiscoveryConfig = ServiceDiscoveryConfig.builder().build();
        }
        log.info("ServiceDiscovery : " + serviceDiscoveryConfig);
        if (revolverConfig.getServiceResolverConfig() != null) {
            serviceNameResolver = revolverConfig.getServiceResolverConfig().isUseCurator()
                    ? RevolverServiceResolver.usingCurator().curatorFramework(getCurator())
                    .objectMapper(environment.getObjectMapper())
                    .resolverConfig(revolverConfig.getServiceResolverConfig())
                    .serviceDiscoveryConfig(serviceDiscoveryConfig).build()
                    : RevolverServiceResolver.builder()
                            .resolverConfig(revolverConfig.getServiceResolverConfig())
                            .objectMapper(environment.getObjectMapper()).
                                    serviceDiscoveryConfig(serviceDiscoveryConfig).build();
        } else {
            serviceNameResolver = RevolverServiceResolver.builder()
                    .objectMapper(environment.getObjectMapper())
                    .serviceDiscoveryConfig(serviceDiscoveryConfig).build();
        }
        loadServiceConfiguration(revolverConfig);
        try {
            serviceNameResolver.getExecutorService()
                    .awaitTermination(serviceDiscoveryConfig.getWaitForDiscoveryInMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.error("Error occurred in service discovery completion : ", e);
        }
    }

    private static void registerHttpCommand(RevolverServiceConfig config) {
        RevolverHttpServiceConfig httpConfig = (RevolverHttpServiceConfig) config;
        httpConfig.setSecured(false);
        registerCommand(config, httpConfig);

        if (serviceConfig.containsKey(httpConfig.getService())) {
            serviceConfig.put(config.getService(), httpConfig);
            if (serviceConnectionPoolMap.get(httpConfig.getService()) != null
                    && !serviceConnectionPoolMap.get(httpConfig.getService())
                    .equals(((RevolverHttpServiceConfig) config).getConnectionPoolSize())) {
                RevolverHttpClientFactory.refreshClient(httpConfig);
            }
        } else {
            serviceConfig.put(config.getService(), httpConfig);
        }
        serviceConnectionPoolMap.put(httpConfig.getService(), httpConfig.getConnectionPoolSize());

    }

    private static void generateApiConfigMap(
            RevolverHttpServiceConfig serviceConfiguration) {
        val tokenMatch = Pattern.compile("\\{(([^/])+\\})");
        List<RevolverHttpApiConfig> apis = new ArrayList<>(serviceConfiguration.getApis());
        apis.sort((o1, o2) -> {
            String o1Expr = generatePathExpression(o1.getPath());
            String o2Expr = generatePathExpression(o2.getPath());
            return tokenMatch.matcher(o2Expr).groupCount() - tokenMatch.matcher(o1Expr)
                    .groupCount();
        });
        apis.sort(Comparator.comparing(RevolverHttpApiConfig::getPath));
        apis.forEach(apiConfig -> {
            ApiPathMap apiPathMap = ApiPathMap.builder().api(apiConfig)
                    .path(generatePathExpression(apiConfig.getPath())).build();
            //Update
            int elementIndex = serviceToPathMap
                    .getOrDefault(serviceConfiguration.getService(), Collections.emptyList())
                    .indexOf(apiPathMap);
            if (elementIndex == -1) {
                serviceToPathMap.add(serviceConfiguration.getService(), apiPathMap);
            } else {
                serviceToPathMap.get(serviceConfiguration.getService())
                        .set(elementIndex, apiPathMap);
            }
        });
        ImmutableMap.Builder<String, RevolverHttpApiConfig> configMapBuilder = ImmutableMap
                .builder();
        apis.forEach(apiConfig -> configMapBuilder.put(apiConfig.getApi(), apiConfig));
    }

    private static String generatePathExpression(String path) {
        if (Strings.isNullOrEmpty(path)) {
            return "";
        }
        return path.replaceAll("\\{(([^/])+\\})", "(([^/])+)");
    }

    private void registerTypes(Bootstrap<?> bootstrap) {
        bootstrap.getObjectMapper()
                .registerModule(new MetricsModule(TimeUnit.MINUTES, TimeUnit.MILLISECONDS, false));
        bootstrap.getObjectMapper()
                .registerSubtypes(new NamedType(RevolverHttpServiceConfig.class, "http"));
        bootstrap.getObjectMapper()
                .registerSubtypes(new NamedType(RevolverHttpsServiceConfig.class, "https"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(BasicAuthConfig.class, "basic"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(TokenAuthConfig.class, "token"));
        bootstrap.getObjectMapper()
                .registerSubtypes(new NamedType(SimpleEndpointSpec.class, "simple"));
        bootstrap.getObjectMapper()
                .registerSubtypes(new NamedType(RangerEndpointSpec.class, "ranger_sharded"));
        bootstrap.getObjectMapper()
                .registerSubtypes(new NamedType(InMemoryMailBoxConfig.class, "in_memory"));
        bootstrap.getObjectMapper()
                .registerSubtypes(new NamedType(AerospikeMailBoxConfig.class, "aerospike"));
    }


    private void registerResources(Environment environment, MetricRegistry metrics,
            PersistenceProvider persistenceProvider, InlineCallbackHandler callbackHandler) {
        environment.jersey().register(
                new RevolverRequestResource(environment.getObjectMapper(), MSG_PACK_OBJECT_MAPPER,
                        persistenceProvider, callbackHandler, metrics, revolverConfig));
        environment.jersey()
                .register(new RevolverCallbackResource(persistenceProvider, callbackHandler));
        environment.jersey().register(new RevolverMetadataResource(revolverConfig));
        environment.jersey().register(
                new RevolverMailboxResource(persistenceProvider, environment.getObjectMapper(),
                        MSG_PACK_OBJECT_MAPPER, Collections.unmodifiableMap(apiConfig)));

        DynamicConfigHandler dynamicConfigHandler = new DynamicConfigHandler(
                getRevolverConfigAttribute(), revolverConfig, environment.getObjectMapper(),
                getConfigSource(), this);
        //Register dynamic config poller if it is enabled
        if (revolverConfig.isDynamicConfig()) {
            environment.lifecycle().manage(dynamicConfigHandler);
        }
        environment.jersey().register(new RevolverConfigResource(dynamicConfigHandler));
        environment.jersey().register(new RevolverApiManageResource());
    }

    private void registerFilters(Environment environment) {
        environment.jersey().register(new RevolverRequestFilter(revolverConfig));
    }

    private void registerMappers(Environment environment) {
        environment.jersey().register(
                new RevolverExceptionMapper(environment.getObjectMapper(), MSG_PACK_OBJECT_MAPPER));
        environment.jersey().register(new TimeoutExceptionMapper(environment.getObjectMapper()));
    }

}

