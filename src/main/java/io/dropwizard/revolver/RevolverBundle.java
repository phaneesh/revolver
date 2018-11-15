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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
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
import io.dropwizard.revolver.callback.CallbackHandler;
import io.dropwizard.revolver.core.RevolverExecutionException;
import io.dropwizard.revolver.core.config.AerospikeMailBoxConfig;
import io.dropwizard.revolver.core.config.InMemoryMailBoxConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
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
import io.dropwizard.revolver.persistence.AeroSpikePersistenceProvider;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.resource.*;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.xml.XmlBundle;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author phaneesh
 */
@Slf4j
public abstract class RevolverBundle<T extends Configuration> implements ConfiguredBundle<T> {

    private static ConcurrentHashMap<String, RevolverHttpCommand> httpCommands = new ConcurrentHashMap<>();

    private static MultivaluedMap<String, ApiPathMap> serviceToPathMap = new MultivaluedHashMap<>();

    public static final ObjectMapper msgPackObjectMapper = new ObjectMapper(new MessagePackFactory());

    public static final XmlMapper xmlObjectMapper = new XmlMapper();

    private static RevolverServiceResolver serviceNameResolver = null;

    public static ConcurrentHashMap<String, Boolean> apiStatus = new ConcurrentHashMap<>();

    @Override
    public void initialize(final Bootstrap<?> bootstrap) {
        //Reset everything before configuration
        HystrixPlugins.reset();
        registerTypes(bootstrap);
        configureXmlMapper();
        bootstrap.addBundle(new XmlBundle());
        bootstrap.addBundle(new MsgPackBundle());
        bootstrap.addBundle(new AssetsBundle("/revolver/dashboard/", "/revolver/dashboard/", "index.html"));
    }

    @Override
    public void run(final T configuration, final Environment environment) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        //Add metrics publisher
        final HystrixCodaHaleMetricsPublisher metricsPublisher = new HystrixCodaHaleMetricsPublisher(environment.metrics());
        HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
        initializeRevolver(configuration, environment);
        final RevolverConfig revolverConfig = getRevolverConfig(configuration);
        if(Strings.isNullOrEmpty(revolverConfig.getHystrixStreamPath())) {
            environment.getApplicationContext().addServlet(HystrixMetricsStreamServlet.class, "/hystrix.stream");
        } else {
            environment.getApplicationContext().addServlet(HystrixMetricsStreamServlet.class, revolverConfig.getHystrixStreamPath());
        }
        environment.jersey().register(new RevolverExceptionMapper(environment.getObjectMapper(), xmlObjectMapper, msgPackObjectMapper));
        environment.jersey().register(new TimeoutExceptionMapper(environment.getObjectMapper()));
        final PersistenceProvider persistenceProvider = getPersistenceProvider(configuration, environment);
        final CallbackHandler callbackHandler = CallbackHandler.builder()
                .persistenceProvider(persistenceProvider)
                .revolverConfig(revolverConfig)
                .build();
        environment.jersey().register(new RevolverRequestFilter(revolverConfig));
        environment.jersey().register(new RevolverRequestResource(environment.getObjectMapper(),
                msgPackObjectMapper, xmlObjectMapper, persistenceProvider, callbackHandler));
        environment.jersey().register(new RevolverCallbackResource(persistenceProvider, callbackHandler));
        environment.jersey().register(new RevolverMailboxResource(persistenceProvider, environment.getObjectMapper(),
                xmlObjectMapper, msgPackObjectMapper));
        environment.jersey().register(new RevolverMetadataResource(revolverConfig));

        DynamicConfigHandler dynamicConfigHandler = new
                DynamicConfigHandler(getRevolverConfigAttribute(), revolverConfig, environment.getObjectMapper(), getConfigSource());
        //Register dynamic config poller if it is enabled
        if(revolverConfig.isDynamicConfig()) {
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

    private void configureXmlMapper() {
        xmlObjectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        xmlObjectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        xmlObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        xmlObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        xmlObjectMapper.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, true);
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
        apis.forEach(apiConfig -> serviceToPathMap.add(serviceConfiguration.getService(),
                ApiPathMap.builder()
                        .api(apiConfig)
                        .path(generatePathExpression(apiConfig.getPath())).build()));
        final ImmutableMap.Builder<String, RevolverHttpApiConfig> configMapBuilder = ImmutableMap.builder();
        apis.forEach(apiConfig -> configMapBuilder.put(apiConfig.getApi(), apiConfig));
        return configMapBuilder.build();
    }

    private static String generatePathExpression(final String path) {
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

    public static RevolverHttpCommand getHttpCommand(final String service) {
        val command = httpCommands.get(service);
        if (null == command) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST, "No service spec defined for service: " + service);
        }
        return command;
    }

    public static RevolverServiceResolver getServiceNameResolver() {
        return serviceNameResolver;
    }

    public abstract RevolverConfig getRevolverConfig(final T configuration);

    public abstract String getRevolverConfigAttribute();

    public abstract ConfigSource getConfigSource();

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
                AerospikeConnectionManager.init((AerospikeMailBoxConfig)revolverConfig.getMailBox());
                return new AeroSpikePersistenceProvider((AerospikeMailBoxConfig)revolverConfig.getMailBox(), environment.getObjectMapper());
        }
        throw new IllegalArgumentException("Invalid mailbox configuration");
    }

    public abstract CuratorFramework getCurator();

    private void initializeRevolver(final T configuration, final Environment environment) throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        final RevolverConfig revolverConfig = getRevolverConfig(configuration);
        if(revolverConfig.getServiceResolverConfig() != null) {
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
        System.out.println("***************************************************************************************************");
        System.out.println("Revolver Service Map");
        System.out.println("***************************************************************************************************");
        serviceToPathMap.forEach( (k, v) -> {
            System.out.println("\tService: " +k);
            v.forEach( a -> a.getApi().getMethods().forEach(b -> System.out.println("\t\t[" +b.name() +"] " + a.getApi().getApi() +": " +a.getPath())));
        });
        System.out.println("***************************************************************************************************");
    }

    public static void loadServiceConfiguration(RevolverConfig revolverConfig) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException, UnrecoverableKeyException {
        for (final RevolverServiceConfig config : revolverConfig.getServices()) {
            final String type = config.getType();
            switch (type) {
                case "http":
                    registerHttpCommand(revolverConfig, config);
                    break;
                case "https":
                    registerHttpsCommand(revolverConfig, config);
                    break;
                default:
                    log.warn("Unsupported Service type: " + type);

            }
        }
    }

    private static void registerHttpsCommand(RevolverConfig revolverConfig, RevolverServiceConfig config) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException, UnrecoverableKeyException {
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
        try {
            registerCommand(revolverConfig, config, revolverHttpServiceConfig);
        } catch (ExecutionException e) {
            log.error("Error creating http command: {}", config.getService(), e);
        }
    }

    private static void registerCommand(RevolverConfig revolverConfig, RevolverServiceConfig config, RevolverHttpServiceConfig revolverHttpServiceConfig) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException, UnrecoverableKeyException, ExecutionException {
        RevolverHttpCommand command = RevolverHttpCommand.builder()
                .clientConfiguration(revolverConfig.getClientConfig())
                .runtimeConfig(revolverConfig.getGlobal())
                .serviceConfiguration(revolverHttpServiceConfig).apiConfigurations(generateApiConfigMap(revolverHttpServiceConfig))
                .serviceResolver(serviceNameResolver)
                .traceCollector(trace -> {
                    //TODO: Put in a publisher if required
                }).build();
        httpCommands.put(config.getService(), command);
        if(config instanceof RevolverHttpServiceConfig) {
            ((RevolverHttpServiceConfig) config).getApis().forEach(a ->
                    apiStatus.put(config.getService() + "." + a.getApi(), true));
        }
        RevolverHttpClientFactory.initClient(revolverHttpServiceConfig);
    }

    private static void registerHttpCommand(RevolverConfig revolverConfig, RevolverServiceConfig config) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException, UnrecoverableKeyException {
        final RevolverHttpServiceConfig httpConfig = (RevolverHttpServiceConfig) config;
        httpConfig.setSecured(false);
        try {
            registerCommand(revolverConfig, config, httpConfig);
        } catch (ExecutionException e) {
            log.error("Error creating http command: {}", config.getService(), e);
        }
    }

    public static void addHttpCommand(String service, RevolverHttpCommand httpCommand) {
        httpCommands.put(service, httpCommand);
    }

}
