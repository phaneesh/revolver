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

import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet;
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
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
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
import io.dropwizard.revolver.http.auth.BasicAuthConfig;
import io.dropwizard.revolver.http.auth.TokenAuthConfig;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.config.RevolverHttpsServiceConfig;
import io.dropwizard.revolver.http.model.ApiPathMap;
import io.dropwizard.revolver.optimizer.OptimizerConfigUpdater;
import io.dropwizard.revolver.optimizer.OptimizerMetricsCache;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.OptimizerMetricsCollector;
import io.dropwizard.revolver.persistence.AeroSpikePersistenceProvider;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.resource.*;
import io.dropwizard.revolver.splitting.SplitConfig;
import io.dropwizard.riemann.RiemannBundle;
import io.dropwizard.riemann.RiemannConfig;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author phaneesh
 */
@Slf4j
public abstract class RevolverBundle<T extends Configuration> implements ConfiguredBundle<T> {

    private static ConcurrentHashMap<String, RevolverHttpServiceConfig> serviceConfig = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<String, RevolverHttpApiConfig> apiConfig = new ConcurrentHashMap<>();

    private static MultivaluedMap<String, ApiPathMap> serviceToPathMap = new MultivaluedHashMap<>();

    public static final ObjectMapper msgPackObjectMapper = new ObjectMapper(new MessagePackFactory());

    public static RevolverServiceResolver serviceNameResolver = null;

    public static ConcurrentHashMap<String, Boolean> apiStatus = new ConcurrentHashMap<>();

    private static Map<String, Integer> serviceConnectionPoolMap = new ConcurrentHashMap<>();

    private static RevolverConfig revolverConfig;

    @Override
    public void initialize(final Bootstrap<?> bootstrap) {
        //Reset everything before configuration
        HystrixPlugins.reset();
        registerTypes(bootstrap);
        bootstrap.addBundle(new MsgPackBundle());
        bootstrap.addBundle(new AssetsBundle("/revolver/dashboard/", "/revolver/dashboard/", "index.html"));
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
    public void run(final T configuration, final Environment environment) {
        //Add metrics publisher
        final HystrixCodaHaleMetricsPublisher metricsPublisher = new HystrixCodaHaleMetricsPublisher(environment.metrics());
        val metrics = environment.metrics();
        ScheduledExecutorService scheduledExecutorService = environment.lifecycle().scheduledExecutorService("metrics-builder").build();

        HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
        initializeRevolver(configuration, environment);
        final RevolverConfig revolverConfig = getRevolverConfig(configuration);
        if (Strings.isNullOrEmpty(revolverConfig.getHystrixStreamPath())) {
            environment.getApplicationContext().addServlet(HystrixMetricsStreamServlet.class, "/hystrix.stream");
        } else {
            environment.getApplicationContext().addServlet(HystrixMetricsStreamServlet.class, revolverConfig.getHystrixStreamPath());
        }
        environment.jersey().register(new RevolverExceptionMapper(environment.getObjectMapper(), msgPackObjectMapper));
        environment.jersey().register(new TimeoutExceptionMapper(environment.getObjectMapper()));

        final PersistenceProvider persistenceProvider = getPersistenceProvider(configuration, environment);
        final InlineCallbackHandler callbackHandler = InlineCallbackHandler.builder()
                .persistenceProvider(persistenceProvider)
                .revolverConfig(revolverConfig)
                .build();

        OptimizerConfig optimizerConfig = revolverConfig.getOptimizerConfig();

        if(optimizerConfig != null && optimizerConfig.isEnabled()){
            OptimizerMetricsCache optimizerMetricsCache = OptimizerMetricsCache.builder().
                    optimizerMetricsCollectorConfig(optimizerConfig.getMetricsCollectorConfig()).build();
            OptimizerMetricsCollector optimizerMetricsCollector = OptimizerMetricsCollector.builder().metrics(metrics)
                    .optimizerMetricsCache(optimizerMetricsCache).build();
            scheduledExecutorService.scheduleAtFixedRate(optimizerMetricsCollector, optimizerConfig.getInitialDelay(), optimizerConfig
                                                                 .getMetricsCollectorConfig().getRepeatAfter(),
                                                         optimizerConfig.getTimeUnit());
            OptimizerConfigUpdater optimizerConfigUpdater = OptimizerConfigUpdater.builder().optimizerConfig(optimizerConfig)
                    .optimizerMetricsCache(optimizerMetricsCache).revolverConfig(revolverConfig).build();
            scheduledExecutorService.scheduleAtFixedRate(optimizerConfigUpdater, optimizerConfig.getInitialDelay(), optimizerConfig
                    .getConfigUpdaterConfig().getRepeatAfter(), optimizerConfig.getTimeUnit());
        }

        environment.jersey().register(new RevolverRequestFilter(revolverConfig));

        environment.jersey().register(new RevolverRequestResource(environment.getObjectMapper(),
                msgPackObjectMapper, persistenceProvider, callbackHandler, metrics));
        environment.jersey().register(new RevolverCallbackResource(persistenceProvider, callbackHandler));
        environment.jersey().register(new RevolverMailboxResource(persistenceProvider, environment.getObjectMapper(),
                msgPackObjectMapper));
        environment.jersey().register(new RevolverMetadataResource(revolverConfig));

        DynamicConfigHandler dynamicConfigHandler = new
                DynamicConfigHandler(getRevolverConfigAttribute(), revolverConfig,
                environment.getObjectMapper(), getConfigSource(), this);
        //Register dynamic config poller if it is enabled
        if (revolverConfig.isDynamicConfig()) {
            environment.lifecycle().manage(dynamicConfigHandler);
        }
        environment.jersey().register(new RevolverConfigResource(dynamicConfigHandler));
        environment.jersey().register(new RevolverApiManageResource());
    }

    private void registerTypes(final Bootstrap<?> bootstrap) {
        bootstrap.getObjectMapper().registerModule(new MetricsModule(TimeUnit.MINUTES, TimeUnit.MILLISECONDS, false));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(RevolverHttpServiceConfig.class, "http"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(RevolverHttpsServiceConfig.class, "https"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(BasicAuthConfig.class, "basic"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(TokenAuthConfig.class, "token"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(SimpleEndpointSpec.class, "simple"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(RangerEndpointSpec.class, "ranger_sharded"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(InMemoryMailBoxConfig.class, "in_memory"));
        bootstrap.getObjectMapper().registerSubtypes(new NamedType(AerospikeMailBoxConfig.class, "aerospike"));
    }

    private static Map<String, RevolverHttpApiConfig> generateApiConfigMap(final RevolverHttpServiceConfig serviceConfiguration) {
        val tokenMatch = Pattern.compile("\\{(([^/])+\\})");
        List<RevolverHttpApiConfig> apis = new ArrayList<>(serviceConfiguration.getApis());
        apis.sort((o1, o2) -> {
            String o1Expr = generatePathExpression(o1.getPath());
            String o2Expr = generatePathExpression(o2.getPath());
            return tokenMatch.matcher(o2Expr).groupCount() - tokenMatch.matcher(o1Expr).groupCount();
        });
        apis.sort(Comparator.comparing(RevolverHttpApiConfig::getPath));
        apis.forEach(apiConfig -> {
            final ApiPathMap apiPathMap = ApiPathMap.builder()
                    .api(apiConfig)
                    .path(generatePathExpression(apiConfig.getPath())).build();
            //Update
            int elementIndex = serviceToPathMap.getOrDefault(serviceConfiguration.getService(), Collections.emptyList())
                    .indexOf(apiPathMap);
            if (elementIndex == -1) {
                serviceToPathMap.add(serviceConfiguration.getService(), apiPathMap);
            } else {
                serviceToPathMap.get(serviceConfiguration.getService()).set(elementIndex, apiPathMap);
            }
        });
        final ImmutableMap.Builder<String, RevolverHttpApiConfig> configMapBuilder = ImmutableMap.builder();
        apis.forEach(apiConfig -> configMapBuilder.put(apiConfig.getApi(), apiConfig));
        return configMapBuilder.build();
    }

    private static String generatePathExpression(final String path) {
        if (Strings.isNullOrEmpty(path)) {
            return "";
        }
        return path.replaceAll("\\{(([^/])+\\})", "(([^/])+)");
    }

    public static ApiPathMap matchPath(final String service, final String path) {
        if (serviceToPathMap.containsKey(service)) {
            final val apiMap = serviceToPathMap.get(service).stream().filter(api -> path.matches(api.getPath())).findFirst();
            return apiMap.orElse(null);
        } else {
            return null;
        }
    }

    public static RevolverHttpCommand getHttpCommand(final String service, final String api) {
        if (!serviceConfig.containsKey(service)) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST, "No service spec defined for service: " + service);
        }
        final String serviceKey = service + "." + api;
        if (!apiConfig.containsKey(serviceKey)) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST, "No api spec defined for service: " + service);
        }
        return RevolverHttpCommand.builder()
                .apiConfiguration(apiConfig.get(serviceKey))
                .clientConfiguration(revolverConfig.getClientConfig())
                .runtimeConfig(revolverConfig.getGlobal())
                .serviceConfiguration(serviceConfig.get(service))
                .build();
    }

    private static RevolverHttpCommand getTestHttpCommand(final RevolverHttpServiceConfig serviceConfig) {
        return RevolverHttpCommand.builder()
                .apiConfiguration(RevolverHttpApiConfig.configBuilder()
                            .method(RevolverHttpApiConfig.RequestMethod.GET)
                            .api("test")
                            .path("/")
                        .build())
                .clientConfiguration(revolverConfig.getClientConfig())
                .runtimeConfig(revolverConfig.getGlobal())
                .serviceConfiguration(serviceConfig)
                .build();
    }

    public static RevolverServiceResolver getServiceNameResolver() {
        return serviceNameResolver;
    }

    public abstract RevolverConfig getRevolverConfig(final T configuration);

    public abstract String getRevolverConfigAttribute();

    public abstract ConfigSource getConfigSource();

    public void onConfigChange(final String configData) {
        log.info("Config changed! Override to propagate config changes to other bundles");
    }

    PersistenceProvider getPersistenceProvider(final T configuration, final Environment environment) {
        final RevolverConfig revolverConfig = getRevolverConfig(configuration);
        //Default for avoiding no mailbox config NPE
        if (revolverConfig.getMailBox() == null) {
            return new InMemoryPersistenceProvider();
        }
        switch (revolverConfig.getMailBox().getType()) {
            case "in_memory":
                return new InMemoryPersistenceProvider();
            case "aerospike":
                AerospikeConnectionManager.init((AerospikeMailBoxConfig) revolverConfig.getMailBox());
                return new AeroSpikePersistenceProvider((AerospikeMailBoxConfig) revolverConfig.getMailBox(), environment.getObjectMapper());
        }
        throw new IllegalArgumentException("Invalid mailbox configuration");
    }

    public abstract CuratorFramework getCurator();

    private void initializeRevolver(final T configuration, final Environment environment) {
        revolverConfig = getRevolverConfig(configuration);
        if (revolverConfig.getServiceResolverConfig() != null) {
            serviceNameResolver = revolverConfig.getServiceResolverConfig().isUseCurator() ? RevolverServiceResolver.usingCurator()
                    .curatorFramework(getCurator())
                    .objectMapper(environment.getObjectMapper())
                    .resolverConfig(revolverConfig.getServiceResolverConfig())
                    .build() : RevolverServiceResolver.builder()
                    .resolverConfig(revolverConfig.getServiceResolverConfig())
                    .objectMapper(environment.getObjectMapper())
                    .build();
        } else {
            serviceNameResolver = RevolverServiceResolver.builder()
                    .objectMapper(environment.getObjectMapper())
                    .build();
        }
        loadServiceConfiguration(revolverConfig);
    }

    public static void loadServiceConfiguration(RevolverConfig revolverConfig) {
        for (final RevolverServiceConfig config : revolverConfig.getServices()) {
            final String type = config.getType();
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
        RevolverBundle.revolverConfig = revolverConfig;
    }

    private static void registerHttpsCommand(RevolverServiceConfig config) {
        final RevolverHttpsServiceConfig httpsConfig = (RevolverHttpsServiceConfig) config;
        final RevolverHttpServiceConfig revolverHttpServiceConfig = RevolverHttpServiceConfig.builder()
                .apis(httpsConfig.getApis())
                .auth(httpsConfig.getAuth())
                .authEnabled(httpsConfig.isAuthEnabled())
                .compression(httpsConfig.isCompression())
                .connectionKeepAliveInMillis(httpsConfig.getConnectionKeepAliveInMillis())
                .connectionPoolSize(httpsConfig.getConnectionPoolSize())
                .enpoint(httpsConfig.getEndpoint())
                .keystorePassword(httpsConfig.getKeystorePassword())
                .keyStorePath(httpsConfig.getKeyStorePath())
                .secured(true)
                .service(httpsConfig.getService())
                .trackingHeaders(httpsConfig.isTrackingHeaders())
                .type(httpsConfig.getType())
                .build();
        registerCommand(config, revolverHttpServiceConfig);
    }

    private static void registerCommand(RevolverServiceConfig config, RevolverHttpServiceConfig revolverHttpServiceConfig) {
        if (config instanceof RevolverHttpServiceConfig) {
            //Adjust connectionPool size to make sure we don't starve connections. Guard against misconfiguration
            //1. Add concurrency from thread pool groups
            //2. Add concurrency from apis which do not belong to any thread pool group
            int totalConcurrency = 0;
            if (config.getThreadPoolGroupConfig() != null) {
                totalConcurrency = config.getThreadPoolGroupConfig().getThreadPools()
                        .stream().mapToInt(ThreadPoolConfig::getConcurrency).sum();
            }
            totalConcurrency += ((RevolverHttpServiceConfig) config).getApis().stream()
                    .filter(a -> Strings.isNullOrEmpty(a.getRuntime().getThreadPool().getThreadPoolName()))
                    .mapToInt(a -> a.getRuntime().getThreadPool().getConcurrency()).sum();

            ((RevolverHttpServiceConfig) config).setConnectionPoolSize(totalConcurrency);

            ((RevolverHttpServiceConfig) config).getApis().forEach(a -> {
                final String key = config.getService() + "." + a.getApi();
                apiStatus.put(key, true);
                apiConfig.put(key, a);
                if (null != a.getSplitConfig() && a.getSplitConfig().isEnabled()) {
                    updateSplitConfig(a);
                }
            });
            generateApiConfigMap((RevolverHttpServiceConfig) config);
            serviceNameResolver.register(revolverHttpServiceConfig.getEndpoint());
        }
    }

    private static void updateSplitConfig(RevolverHttpApiConfig apiConfig) {
        double from = 0.0;
        for (SplitConfig splitConfig : apiConfig.getSplitConfig()
                .getSplits()) {
            double wrr = splitConfig.getWrr();
            splitConfig.setFrom(from);
            from += wrr;
            splitConfig.setTo(from);
        }
        if (from > 1.0) {
            throw new RuntimeException("wrr of split api is exceeding weight of 1");
        }
    }

    private static void registerHttpCommand(RevolverServiceConfig config) {
        final RevolverHttpServiceConfig httpConfig = (RevolverHttpServiceConfig) config;
        httpConfig.setSecured(false);
        registerCommand(config, httpConfig);

        if(serviceConfig.containsKey(httpConfig.getService())) {
            serviceConfig.put(config.getService(), httpConfig);
            if(serviceConnectionPoolMap.get(httpConfig.getService()) != null && !serviceConnectionPoolMap.get(httpConfig.getService())
                    .equals(((RevolverHttpServiceConfig)config).getConnectionPoolSize()) ){
                RevolverHttpClientFactory.refreshClient(httpConfig);
            }
        } else {
            serviceConfig.put(config.getService(), httpConfig);
        }
        serviceConnectionPoolMap.put(httpConfig.getService(), httpConfig.getConnectionPoolSize());

    }

    public static void addHttpCommand(RevolverHttpServiceConfig config) {
        if (!serviceConfig.containsKey(config.getService())) {
            serviceConfig.put(config.getService(), config);
            registerCommand(config, config);
        }
    }

    public MultivaluedMap<String, ApiPathMap> getServiceToPathMap() {
        return serviceToPathMap;
    }

    public static ConcurrentHashMap<String, RevolverHttpServiceConfig> getServiceConfig() {
        return serviceConfig;
    }

}
