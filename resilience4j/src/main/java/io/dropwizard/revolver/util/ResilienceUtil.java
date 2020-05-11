package io.dropwizard.revolver.util;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.ThreadPoolGroupConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.core.config.resilience.BulkHeadConfig;
import io.dropwizard.revolver.core.config.resilience.ResilienceConfig;
import io.dropwizard.revolver.http.ResilienceHttpContext;
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
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/***
 Created by nitish.goyal on 23/11/19
 ***/
@Slf4j
public class ResilienceUtil {

    public static final String BULK_HEAD_DELIMITER = "-";
    public static final String DEFAULT_CIRCUIT_BREAKER = "revolver";
    private static final Map<String, String> DEFAULT_KEY_VALUE_MAP = Maps.newHashMap();
    private static final String METRIC_PREFIX = "resilience";
    private static final Map<String, Bulkhead> POOL_VS_BULK_HEAD = Maps.newHashMap();
    public static CircuitBreakerRegistry circuitBreakerRegistry =
            CircuitBreakerRegistry.ofDefaults();
    private static BulkheadRegistry bulkheadRegistry = BulkheadRegistry.ofDefaults();

    static {
        DEFAULT_KEY_VALUE_MAP.put(METRIC_PREFIX + ".step", "PT1M");
        DEFAULT_KEY_VALUE_MAP.put(METRIC_PREFIX + ".enabled", "true");
    }
    private ResilienceUtil() {
    }

    public static void initializeResilience(RevolverConfig revolverConfig,
            ResilienceHttpContext resilienceHttpContext) {

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

            updateCBForApiConfigs(apiVsCircuitBreaker, revolverServiceConfig);
            updateCBForDefaultServiceConfig(apiVsCircuitBreaker, revolverServiceConfig);

        }
        apiVsCircuitBreaker.forEach((s, circuitBreaker) -> {
            if (log.isDebugEnabled()) {
                log.debug("Resilience circuit breaker : {}, circuit break config : {} ", s,
                        circuitBreaker.getCircuitBreakerConfig()
                                .getFailureRateThreshold());
            }

        });
        resilienceHttpContext.setApiVsCircuitBreaker(apiVsCircuitBreaker);
    }

    private static void initializeBulkHeads(RevolverConfig revolverConfig,
            ResilienceHttpContext resilienceHttpContext) {

        ResilienceConfig resilienceConfig = revolverConfig.getResilienceConfig();
        for (RevolverServiceConfig revolverServiceConfig : revolverConfig.getServices()) {

            updateBulkheadsForThreadPools(revolverServiceConfig, resilienceConfig);
            updateBulkheadsForApiConfigs(revolverServiceConfig, resilienceConfig);
            updateBulkHeadsForDefaultServiceConfig(revolverServiceConfig, resilienceConfig);
        }

        POOL_VS_BULK_HEAD.forEach((s, bulkhead) -> {
            if (log.isDebugEnabled()) {
                log.debug("Resilience bulk head Key : {}, bulk head value : {}, maxWaitTime : {}", s,
                        bulkhead.getBulkheadConfig()
                                .getMaxConcurrentCalls(), bulkhead.getBulkheadConfig()
                                .getMaxWaitDuration());
            }
        });
        resilienceHttpContext.setPoolVsBulkHeadMap(POOL_VS_BULK_HEAD);
    }

    private static void initializeTimeout(RevolverConfig revolverConfig, ResilienceHttpContext resilienceHttpContext) {
        Map<String, Integer> poolVsTimeout = Maps.newHashMap();
        Map<String, Integer> apiVsTimeout = Maps.newHashMap();

        for (RevolverServiceConfig revolverServiceConfig : revolverConfig.getServices()) {

            updateTimeoutsForThreadPools(poolVsTimeout, revolverServiceConfig);
            updateTimeoutsForApiConfigs(poolVsTimeout, apiVsTimeout, revolverServiceConfig);
            updateTimeoutsForDefaultServiceConfig(poolVsTimeout, revolverServiceConfig);
        }

        apiVsTimeout.forEach((s, timeout) -> {
            if (log.isDebugEnabled()) {
                log.debug("Resilience timeout  Key : {}, timeout value : {} ", s, timeout);
            }
        });
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

    private static void updateCBForDefaultServiceConfig(Map<String, CircuitBreaker> apiVsCircuitBreaker,
            RevolverServiceConfig revolverServiceConfig) {
        apiVsCircuitBreaker.put(revolverServiceConfig.getService(),
                circuitBreakerRegistry.circuitBreaker(revolverServiceConfig.getService()));

    }

    private static void updateBulkheadsForApiConfigs(RevolverServiceConfig revolverServiceConfig,
            ResilienceConfig resilienceConfig) {
        if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
            ((RevolverHttpServiceConfig) revolverServiceConfig).getApis()
                    .forEach(revolverHttpApiConfig -> {
                        if (revolverHttpApiConfig.getRuntime() != null) {
                            HystrixCommandConfig hystrixCommandConfig = revolverHttpApiConfig.getRuntime();
                            if (hystrixCommandConfig == null || hystrixCommandConfig.getThreadPool() == null) {
                                return;
                            }
                            String threadPoolName = ThreadPoolUtil.getThreadPoolName(revolverServiceConfig,
                                    hystrixCommandConfig.getThreadPool());
                            if (StringUtils.isNotEmpty(threadPoolName)) {
                                return;
                            }
                            threadPoolName =
                                    revolverServiceConfig.getService() + BULK_HEAD_DELIMITER + revolverHttpApiConfig
                                            .getApi();
                            if (log.isDebugEnabled()) {
                                log.debug("ThreadPool Name : {}, Concurrency : {} ", threadPoolName,
                                        hystrixCommandConfig.getThreadPool()
                                                .getConcurrency());
                            }
                            updateBulkheadRegistry(hystrixCommandConfig.getThreadPool(),
                                    threadPoolName, resilienceConfig);
                        }
                    });
        }
    }

    private static void updateBulkheadRegistry(ThreadPoolConfig threadPoolConfig, String threadPoolName,
            ResilienceConfig resilienceConfig) {
        BulkHeadConfig bulkHeadConfig = getBulkHeadConfig(resilienceConfig);
        if (POOL_VS_BULK_HEAD.get(threadPoolName) == null) {
            POOL_VS_BULK_HEAD.put(threadPoolName, bulkheadRegistry.bulkhead(
                    threadPoolName,
                    BulkheadConfig.custom().maxConcurrentCalls(
                            threadPoolConfig.getConcurrency())
                            .maxWaitDuration(Duration.ofMillis(bulkHeadConfig.getMaxWaitTimeInMillis()))
                            .build()));
        } else {
            Bulkhead bulkhead = Bulkhead.of(threadPoolName,
                    BulkheadConfig.custom()
                            .maxConcurrentCalls(threadPoolConfig.getConcurrency())
                            .maxWaitDuration(Duration.ofMillis(bulkHeadConfig.getMaxWaitTimeInMillis()))
                            .build());
            bulkheadRegistry.replace(threadPoolName, bulkhead);
            POOL_VS_BULK_HEAD.put(threadPoolName, bulkhead);
        }
    }

    private static BulkHeadConfig getBulkHeadConfig(ResilienceConfig resilienceConfig) {
        BulkHeadConfig bulkHeadConfig;
        if (resilienceConfig != null && resilienceConfig.getBulkHeadConfig() != null) {
            bulkHeadConfig = resilienceConfig.getBulkHeadConfig();
        } else {
            bulkHeadConfig = new BulkHeadConfig();
        }
        return bulkHeadConfig;
    }

    private static void updateBulkheadsForThreadPools(
            RevolverServiceConfig revolverServiceConfig,
            ResilienceConfig resilienceConfig) {
        ThreadPoolGroupConfig threadPoolGroupConfig = revolverServiceConfig.getThreadPoolGroupConfig();
        if (threadPoolGroupConfig != null) {
            threadPoolGroupConfig.getThreadPools().forEach(threadPoolConfig -> {
                String threadPoolName = ThreadPoolUtil.getThreadPoolName(revolverServiceConfig, threadPoolConfig);
                if (log.isDebugEnabled()) {
                    log.debug("ThreadPool Name : {}, Concurrency : {} ", threadPoolName,
                            threadPoolConfig.getConcurrency());
                }
                updateBulkheadRegistry(threadPoolConfig, threadPoolName, resilienceConfig);
            });

        }
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
                            //Setting the timeout which is higher of pool or api level timeout
                            int timeout = threadPoolConfig.getTimeout();
                            String threadPoolName = threadPoolConfig.getThreadPoolName();
                            if (poolVsTimeout.get(threadPoolName) != null) {
                                int poolTimeout = poolVsTimeout.get(threadPoolName);
                                if (poolTimeout > timeout) {
                                    timeout = poolTimeout;
                                }
                            }
                            apiVsTimeout.put(apiName, timeout);
                        }
                    });
        }
    }

    public static String getApiName(RevolverServiceConfig revolverServiceConfig,
            RevolverHttpApiConfig revolverHttpApiConfig) {
        return revolverServiceConfig.getService() + "." + revolverHttpApiConfig.getApi();
    }


    private static void updateBulkHeadsForDefaultServiceConfig(RevolverServiceConfig revolverServiceConfig,
            ResilienceConfig resilienceConfig) {
        BulkHeadConfig bulkHeadConfig = getBulkHeadConfig(resilienceConfig);
        if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
            ThreadPoolConfig threadPoolConfig = revolverServiceConfig.getRuntime().getThreadPool();
            if (threadPoolConfig == null) {
                return;
            }
            if (POOL_VS_BULK_HEAD.get(revolverServiceConfig.getService()) == null) {
                POOL_VS_BULK_HEAD.put(revolverServiceConfig.getService(), bulkheadRegistry
                        .bulkhead(revolverServiceConfig.getService(),
                                BulkheadConfig.custom().maxConcurrentCalls(threadPoolConfig.getConcurrency())
                                        .maxWaitDuration(Duration.ofMillis(bulkHeadConfig.getMaxWaitTimeInMillis()))
                                        .build()));

            } else {
                Bulkhead bulkhead = Bulkhead.of(revolverServiceConfig.getService(),
                        BulkheadConfig.custom().maxConcurrentCalls(threadPoolConfig.getConcurrency())
                                .maxWaitDuration(Duration.ofMillis(bulkHeadConfig.getMaxWaitTimeInMillis())).build());
                bulkheadRegistry.replace(revolverServiceConfig.getService(), bulkhead);
                POOL_VS_BULK_HEAD.put(revolverServiceConfig.getService(), bulkhead);
            }
        }
    }
}
