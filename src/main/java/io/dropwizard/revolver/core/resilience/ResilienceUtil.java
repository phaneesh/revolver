package io.dropwizard.revolver.core.resilience;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.ThreadPoolGroupConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/***
 Created by nitish.goyal on 23/11/19
 ***/
@Slf4j
public class ResilienceUtil {

    private static final Map<String, String> DEFAULT_KEY_VALUE_MAP = Maps.newHashMap();
    private static final String METRIC_PREFIX = "resilience";
    private static final String DEFAULT_CIRCUIT_BREAKER = "revolver";

    static {
        DEFAULT_KEY_VALUE_MAP.put(METRIC_PREFIX + ".step", "PT1M");
        DEFAULT_KEY_VALUE_MAP.put(METRIC_PREFIX + ".enabled", "true");
    }

    private ResilienceUtil() {
    }

    private static BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();
    private static CircuitBreakerRegistry circuitBreakerRegistry =
            CircuitBreakerRegistry.ofDefaults();

    public static void initializeResilience(RevolverConfig revolverConfig,
            ResilienceHttpContext resilienceHttpContext) {

        log.info("Initializing resilience util");

        initializeBulkHeads(revolverConfig, resilienceHttpContext);
        initializeCircuitBreakers(revolverConfig, resilienceHttpContext);
        initializeTimeout(revolverConfig, resilienceHttpContext);
    }

    public static void bindResilienceMetrics(MetricRegistry metrics) {
        io.micrometer.core.instrument.MeterRegistry metricRegistry = new DropwizardMeterRegistry(
                new DropwizardConfig() {
                    @Override
                    public String prefix() {
                        return METRIC_PREFIX;
                    }

                    @Override
                    public String get(String s) {
                        return DEFAULT_KEY_VALUE_MAP.get(s);
                    }
                }, metrics,
                HierarchicalNameMapper.DEFAULT, Clock.SYSTEM) {
            @Override
            protected Double nullGaugeValue() {
                return null;
            }
        };

        TaggedBulkheadMetrics
                .ofBulkheadRegistry(bulkheadRegistry)
                .bindTo(metricRegistry);

        TaggedCircuitBreakerMetrics
                .ofCircuitBreakerRegistry(circuitBreakerRegistry)
                .bindTo(metricRegistry);
    }

    private static void initializeCircuitBreakers(RevolverConfig revolverConfig,
            ResilienceHttpContext resilienceHttpContext) {

        Map<String, CircuitBreaker> apiVsCircuitBreaker = Maps.newHashMap();
        resilienceHttpContext.setDefaultCircuitBreaker(circuitBreakerRegistry.circuitBreaker(DEFAULT_CIRCUIT_BREAKER));

        for (RevolverServiceConfig revolverServiceConfig : revolverConfig.getServices()) {

            //updateCBForThreadPools(apiVsCircuitBreaker, revolverServiceConfig);
            updateCBForApiConfigs(apiVsCircuitBreaker, revolverServiceConfig);
            updateCBForDefaultServiceConfig(apiVsCircuitBreaker, revolverServiceConfig);

        }
        apiVsCircuitBreaker.forEach(
                (s, circuitBreaker) -> log.info("Resilience circuit breaker : {}, circuit break config : {} ", s,
                        circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold()));
        resilienceHttpContext.setApiVsCircuitBreaker(apiVsCircuitBreaker);
    }

    private static void initializeBulkHeads(RevolverConfig revolverConfig,
            ResilienceHttpContext resilienceHttpContext) {
        log.info("Initializing resilience bulk heads");
        Map<String, Bulkhead> poolVsBulkHead = Maps.newHashMap();

        for (RevolverServiceConfig revolverServiceConfig : revolverConfig.getServices()) {

            updateBulkheadsForThreadPools(poolVsBulkHead, revolverServiceConfig);
            updateBulkheadsForApiConfigs(poolVsBulkHead, revolverServiceConfig);
            updateBulkHeadsForDefaultServiceConfig(poolVsBulkHead, revolverServiceConfig);
        }

        poolVsBulkHead.forEach((s, bulkhead) -> log.info("Resilience bulk head Key : {}, bulk head value : {} ", s,
                bulkhead.getBulkheadConfig().getMaxConcurrentCalls()));
        resilienceHttpContext.setPoolVsBulkHeadMap(poolVsBulkHead);
    }

    private static void initializeTimeout(RevolverConfig revolverConfig, ResilienceHttpContext resilienceHttpContext) {
        log.info("Initializing resilience time out");
        Map<String, Integer> poolVsTimeout = Maps.newHashMap();
        Map<String, Integer> apiVsTimeout = Maps.newHashMap();

        for (RevolverServiceConfig revolverServiceConfig : revolverConfig.getServices()) {

            updateTimeoutsForThreadPools(poolVsTimeout, revolverServiceConfig);
            updateTimeoutsForApiConfigs(poolVsTimeout, apiVsTimeout, revolverServiceConfig);
            updateTimeoutsForDefaultServiceConfig(poolVsTimeout, revolverServiceConfig);
        }

        apiVsTimeout
                .forEach((s, timeout) -> log.info("Resilience timeout  Key : {}, timeout value : {} ", s, timeout));
        resilienceHttpContext.setApiVsTimeout(apiVsTimeout);
    }

    private static void updateCBForApiConfigs(Map<String, CircuitBreaker> apiVsCircuitBreaker,
            RevolverServiceConfig revolverServiceConfig) {
        if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
            ((RevolverHttpServiceConfig) revolverServiceConfig).getApis()
                    .forEach(revolverHttpApiConfig -> {
                        String cbName = getCbName(revolverServiceConfig, revolverHttpApiConfig);
                        apiVsCircuitBreaker.putIfAbsent(cbName,
                                circuitBreakerRegistry.circuitBreaker(
                                        cbName));

                    });
        }
    }

    public static String getCbName(RevolverServiceConfig revolverServiceConfig,
            RevolverHttpApiConfig revolverHttpApiConfig) {
        return revolverServiceConfig.getService() + "." + revolverHttpApiConfig.getApi();
    }

    private static void updateCBForThreadPools(Map<String, CircuitBreaker> poolVsCircuitBreaker,
            RevolverServiceConfig revolverServiceConfig) {
        ThreadPoolGroupConfig threadPoolGroupConfig = revolverServiceConfig.getThreadPoolGroupConfig();
        if (threadPoolGroupConfig != null) {
            threadPoolGroupConfig.getThreadPools().forEach(threadPoolConfig -> {
                String threadPoolName =
                        getThreadPoolName(revolverServiceConfig, threadPoolConfig);
                poolVsCircuitBreaker.put(threadPoolName,
                        circuitBreakerRegistry.circuitBreaker(threadPoolName));
            });
        }
    }

    private static void updateCBForDefaultServiceConfig(Map<String, CircuitBreaker> apiVsCircuitBreaker,
            RevolverServiceConfig revolverServiceConfig) {
        apiVsCircuitBreaker.put(revolverServiceConfig.getService(),
                circuitBreakerRegistry.circuitBreaker(revolverServiceConfig.getService()));

    }

    private static void updateBulkheadsForApiConfigs(Map<String, Bulkhead> poolVsBulkHead,
            RevolverServiceConfig revolverServiceConfig) {
        if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
            ((RevolverHttpServiceConfig) revolverServiceConfig).getApis()
                    .forEach(revolverHttpApiConfig -> {
                        if (revolverHttpApiConfig.getRuntime() != null) {
                            HystrixCommandConfig hystrixCommandConfig = revolverHttpApiConfig.getRuntime();
                            if (hystrixCommandConfig == null || hystrixCommandConfig.getThreadPool() == null) {
                                return;
                            }
                            String threadPoolName = getThreadPoolName(revolverServiceConfig,
                                    hystrixCommandConfig.getThreadPool());
                            if (StringUtils.isEmpty(threadPoolName)) {
                                threadPoolName =
                                        revolverServiceConfig.getService() + "." + revolverHttpApiConfig.getApi();
                            }
                            poolVsBulkHead.putIfAbsent(threadPoolName,
                                    bulkheadRegistry.bulkhead(
                                            threadPoolName,
                                            BulkheadConfig.custom().maxConcurrentCalls(
                                                    hystrixCommandConfig.getThreadPool()
                                                            .getConcurrency())
                                                    .build()));
                        }
                    });
        }
    }

    private static void updateBulkheadsForThreadPools(Map<String, Bulkhead> poolVsBulkHead,
            RevolverServiceConfig revolverServiceConfig) {
        ThreadPoolGroupConfig threadPoolGroupConfig = revolverServiceConfig.getThreadPoolGroupConfig();
        if (threadPoolGroupConfig != null) {
            threadPoolGroupConfig.getThreadPools().forEach(threadPoolConfig -> {
                String threadPoolName =
                        getThreadPoolName(revolverServiceConfig, threadPoolConfig);
                log.info("ThreadPool Name : {} ", threadPoolName);
                poolVsBulkHead.put(threadPoolName,
                        bulkheadRegistry.bulkhead(threadPoolName,
                                BulkheadConfig.custom().maxConcurrentCalls(threadPoolConfig.getConcurrency())
                                        .build()));
            });
        }
    }

    private static String getThreadPoolName(RevolverServiceConfig revolverServiceConfig,
            ThreadPoolConfig threadPoolConfig) {
        if (StringUtils.isEmpty(threadPoolConfig.getThreadPoolName())) {
            return StringUtils.EMPTY;
        }
        return revolverServiceConfig.getService() + "." + threadPoolConfig.getThreadPoolName();
    }


    private static void updateTimeoutsForDefaultServiceConfig(Map<String, Integer> poolVsTimeout,
            RevolverServiceConfig revolverServiceConfig) {
        if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
            ThreadPoolConfig threadPoolConfig = revolverServiceConfig.getRuntime().getThreadPool();
            if (threadPoolConfig == null) {
                return;
            }
            poolVsTimeout.put(revolverServiceConfig.getService(), threadPoolConfig.getTimeout());
        }
    }

    private static void updateTimeoutsForThreadPools(Map<String, Integer> poolVsTimeout,
            RevolverServiceConfig revolverServiceConfig) {
        ThreadPoolGroupConfig threadPoolGroupConfig = revolverServiceConfig.getThreadPoolGroupConfig();
        if (threadPoolGroupConfig != null) {
            threadPoolGroupConfig.getThreadPools().forEach(threadPoolConfig -> {
                if (StringUtils.isNotEmpty(threadPoolConfig.getThreadPoolName())) {
                    poolVsTimeout.put(threadPoolConfig.getThreadPoolName(),
                            threadPoolConfig.getTimeout());
                }
            });
        }
    }

    private static void updateTimeoutsForApiConfigs(Map<String, Integer> poolVsTimeout,
            Map<String, Integer> apiVsTimeout, RevolverServiceConfig revolverServiceConfig) {
        if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
            ((RevolverHttpServiceConfig) revolverServiceConfig).getApis()
                    .forEach(revolverHttpApiConfig -> {
                        if (revolverHttpApiConfig.getRuntime() != null) {
                            HystrixCommandConfig hystrixCommandConfig = revolverHttpApiConfig.getRuntime();
                            String apiName = getApiName(revolverServiceConfig, revolverHttpApiConfig);
                            if (hystrixCommandConfig == null || hystrixCommandConfig.getThreadPool() == null) {
                                return;
                            }
                            ThreadPoolConfig threadPoolConfig = hystrixCommandConfig.getThreadPool();
                            if (threadPoolConfig.getTimeout() != 0) {
                                apiVsTimeout.put(apiName, threadPoolConfig.getTimeout());
                                return;
                            }
                            String threadPoolName = threadPoolConfig.getThreadPoolName();
                            if (poolVsTimeout.get(threadPoolName) != null) {
                                apiVsTimeout.put(apiName, poolVsTimeout.get(threadPoolName));
                            }
                        }
                    });
        }
    }

    public static String getApiName(RevolverServiceConfig revolverServiceConfig,
            RevolverHttpApiConfig revolverHttpApiConfig) {
        return revolverServiceConfig.getService() + "." + revolverHttpApiConfig.getApi();
    }


    private static void updateBulkHeadsForDefaultServiceConfig(Map<String, Bulkhead> poolVsBulkHead,
            RevolverServiceConfig revolverServiceConfig) {
        if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
            ThreadPoolConfig threadPoolConfig = revolverServiceConfig.getRuntime().getThreadPool();
            if (threadPoolConfig == null) {
                return;
            }
            poolVsBulkHead.put(revolverServiceConfig.getService(), bulkheadRegistry
                    .bulkhead(revolverServiceConfig.getService(),
                            BulkheadConfig.custom().maxConcurrentCalls(threadPoolConfig.getConcurrency()).build()));

        }
    }
}
