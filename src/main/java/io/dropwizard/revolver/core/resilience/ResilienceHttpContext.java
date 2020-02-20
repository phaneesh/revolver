package io.dropwizard.revolver.core.resilience;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.config.resilience.ResilienceConfig;
import io.dropwizard.revolver.core.config.resilience.ThreadPoolConfig;
import io.dropwizard.revolver.http.RevolverHttpContext;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/***
 Created by nitish.goyal on 23/11/19
 ***/
@Data
@Builder
@AllArgsConstructor
public class ResilienceHttpContext extends RevolverHttpContext {

    private static final String THREAD_POOL_PREFIX = "resilience";

    private CircuitBreaker defaultCircuitBreaker;

    private Map<String, CircuitBreaker> apiVsCircuitBreaker = Maps.newHashMap();

    private Map<String, Bulkhead> poolVsBulkHeadMap = Maps.newHashMap();

    private Map<String, Integer> apiVsTimeout = Maps.newHashMap();

    private ExecutorService executor;

    private MetricRegistry metrics;

    private ResilienceConfig resilienceConfig;

    public ResilienceHttpContext() {
        setupExecutor();
    }

    @Builder
    public ResilienceHttpContext(CircuitBreaker defaultCircuitBreaker,
            Map<String, CircuitBreaker> apiVsCircuitBreaker,
            Map<String, Bulkhead> poolVsBulkHeadMap, Map<String, Integer> apiVsTimeout,
            MetricRegistry metrics, ResilienceConfig resilienceConfig) {
        this.defaultCircuitBreaker = defaultCircuitBreaker;
        this.apiVsCircuitBreaker = apiVsCircuitBreaker;
        this.poolVsBulkHeadMap = poolVsBulkHeadMap;
        this.apiVsTimeout = apiVsTimeout;
        this.metrics = metrics;
        this.resilienceConfig = resilienceConfig;
        setupExecutor();
    }

    public ExecutorService getExecutor() {
        if (executor == null) {
            setupExecutor();
        }
        return executor;
    }

    private void setupExecutor() {
        if (resilienceConfig == null) {
            resilienceConfig = new ResilienceConfig();
        }
        ThreadPoolConfig threadPoolConfig = resilienceConfig.getThreadPoolConfig();
        ThreadFactory threadFactory = new NamedThreadFactory(THREAD_POOL_PREFIX);
        this.executor = new ThreadPoolExecutor(threadPoolConfig.getCorePoolSize(), threadPoolConfig.getMaxPoolSize(),
                threadPoolConfig.getKeepAliveTimeInSeconds(), TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(threadPoolConfig.getQueueSize()), threadFactory);
    }
}
