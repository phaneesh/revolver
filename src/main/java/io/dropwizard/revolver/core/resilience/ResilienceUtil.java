package io.dropwizard.revolver.core.resilience;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.ThreadPoolGroupConfig;
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
            ResilienceHttpContext resilienceHttpContext, MetricRegistry metrics) {

        log.info("Initializing resilience util");
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

        initializeBulkHeads(revolverConfig, resilienceHttpContext);
        initializeCircuitBreakers(revolverConfig, resilienceHttpContext);
        initializeTimeout(revolverConfig, resilienceHttpContext);
    }

    private static void initializeCircuitBreakers(RevolverConfig revolverConfig,
            ResilienceHttpContext resilienceHttpContext) {

        Map<String, CircuitBreaker> poolVsCircuitBreaker = Maps.newHashMap();
        resilienceHttpContext.setDefaultCircuitBreaker(circuitBreakerRegistry.circuitBreaker(DEFAULT_CIRCUIT_BREAKER));

        for (RevolverServiceConfig revolverServiceConfig : revolverConfig.getServices()) {

            ThreadPoolGroupConfig threadPoolGroupConfig = revolverServiceConfig.getThreadPoolGroupConfig();
            if (threadPoolGroupConfig != null) {
                threadPoolGroupConfig.getThreadPools().forEach(threadPoolConfig -> {
                    poolVsCircuitBreaker.put(threadPoolConfig.getThreadPoolName(),
                            circuitBreakerRegistry.circuitBreaker(threadPoolConfig.getThreadPoolName()));
                });
            }

            if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
                ((RevolverHttpServiceConfig) revolverServiceConfig).getApis()
                        .forEach(revolverHttpApiConfig -> {
                            if (revolverHttpApiConfig.getRuntime() != null) {
                                HystrixCommandConfig hystrixCommandConfig = revolverHttpApiConfig.getRuntime();
                                if (hystrixCommandConfig == null || hystrixCommandConfig.getThreadPool() == null) {
                                    return;
                                }
                                String threadPoolName = hystrixCommandConfig.getThreadPool().getThreadPoolName();
                                if (StringUtils.isEmpty(threadPoolName)) {
                                    threadPoolName =
                                            revolverServiceConfig.getService() + "." + revolverHttpApiConfig.getApi();
                                }
                                poolVsCircuitBreaker.putIfAbsent(threadPoolName,
                                        circuitBreakerRegistry.circuitBreaker(
                                                threadPoolName));
                            }
                        });
            }
        }
        poolVsCircuitBreaker.forEach((s, circuitBreaker) -> {
            log.info("Resilience circuit breaker : {}, circuit break config : {} ", s,
                    circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());
        });
        resilienceHttpContext.setPoolVsCircuitBreaker(poolVsCircuitBreaker);
    }

    private static void initializeBulkHeads(RevolverConfig revolverConfig,
            ResilienceHttpContext resilienceHttpContext) {
        log.info("Initializing resilience bulk heads");
        Map<String, Bulkhead> poolVsBulkHead = Maps.newHashMap();

        for (RevolverServiceConfig revolverServiceConfig : revolverConfig.getServices()) {

            ThreadPoolGroupConfig threadPoolGroupConfig = revolverServiceConfig.getThreadPoolGroupConfig();
            if (threadPoolGroupConfig != null) {
                threadPoolGroupConfig.getThreadPools().forEach(threadPoolConfig -> {
                    String bulkHeadName =
                            revolverServiceConfig.getService() + "." + threadPoolConfig.getThreadPoolName();
                    poolVsBulkHead.put(bulkHeadName,
                            bulkheadRegistry.bulkhead(bulkHeadName,
                                    BulkheadConfig.custom().maxConcurrentCalls(threadPoolConfig.getConcurrency())
                                            .build()));
                });
            }

            if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
                ((RevolverHttpServiceConfig) revolverServiceConfig).getApis()
                        .forEach(revolverHttpApiConfig -> {
                            if (revolverHttpApiConfig.getRuntime() != null) {
                                HystrixCommandConfig hystrixCommandConfig = revolverHttpApiConfig.getRuntime();
                                if (hystrixCommandConfig == null || hystrixCommandConfig.getThreadPool() == null) {
                                    return;
                                }
                                String threadPoolName = hystrixCommandConfig.getThreadPool().getThreadPoolName();
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
        poolVsBulkHead.forEach((s, bulkhead) -> {
            log.info("Resilience bulk head Key : {}, bulk head value : {} ", s,
                    bulkhead.getBulkheadConfig().getMaxConcurrentCalls());
        });
        resilienceHttpContext.setPoolVsBulkHeadMap(poolVsBulkHead);
    }

    private static void initializeTimeout(RevolverConfig revolverConfig,
            ResilienceHttpContext resilienceHttpContext) {
        log.info("Initializing resilience time out");
        Map<String, Integer> poolVsTimeout = Maps.newHashMap();

        for (RevolverServiceConfig revolverServiceConfig : revolverConfig.getServices()) {

            ThreadPoolGroupConfig threadPoolGroupConfig = revolverServiceConfig.getThreadPoolGroupConfig();
            if (threadPoolGroupConfig != null) {
                threadPoolGroupConfig.getThreadPools().forEach(threadPoolConfig -> {
                    poolVsTimeout.put(threadPoolConfig.getThreadPoolName(),
                            threadPoolConfig.getTimeout());
                });
            }

            if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
                ((RevolverHttpServiceConfig) revolverServiceConfig).getApis()
                        .forEach(revolverHttpApiConfig -> {
                            if (revolverHttpApiConfig.getRuntime() != null) {
                                HystrixCommandConfig hystrixCommandConfig = revolverHttpApiConfig.getRuntime();
                                if (hystrixCommandConfig == null || hystrixCommandConfig.getThreadPool() == null) {
                                    return;
                                }
                                String threadPoolName = hystrixCommandConfig.getThreadPool().getThreadPoolName();
                                if (StringUtils.isEmpty(threadPoolName)) {
                                    threadPoolName =
                                            revolverServiceConfig.getService() + "." + revolverHttpApiConfig.getApi();
                                }
                                poolVsTimeout.putIfAbsent(threadPoolName,
                                        hystrixCommandConfig.getThreadPool().getTimeout());
                            }
                        });
            }
            poolVsTimeout.forEach((s, timeout) -> {
                log.info("Resilience timeout  Key : {}, timeput value : {} ", s, timeout);
            });
        }
        resilienceHttpContext.setPoolVsTimeout(poolVsTimeout);
    }

}
