package io.dropwizard.revolver.http;

import static io.dropwizard.revolver.util.ResilienceUtil.DEFAULT_CIRCUIT_BREAKER;
import static io.dropwizard.revolver.util.ResilienceUtil.circuitBreakerRegistry;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverConfigHolder;
import io.dropwizard.revolver.core.config.resilience.ResilienceConfig;
import io.dropwizard.revolver.core.config.resilience.ThreadPoolConfig;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.util.ResilienceUtil;
import io.dropwizard.setup.Environment;
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

    private RevolverConfigHolder revolverConfigHolder;

    public ResilienceHttpContext() {
        setupExecutor(new ResilienceConfig());
    }


    @Override
    public void initialize(Environment environment, RevolverConfigHolder revolverConfigHolder, MetricRegistry metrics) {
        this.defaultCircuitBreaker = circuitBreakerRegistry.circuitBreaker(DEFAULT_CIRCUIT_BREAKER);
        this.metrics = metrics;
        this.revolverConfigHolder = revolverConfigHolder;
        setupExecutor(revolverConfigHolder.getConfig().getResilienceConfig());

        ResilienceUtil.bindResilienceMetrics(metrics);
        ResilienceUtil.initializeResilience(revolverConfigHolder.getConfig(), this);
    }

    @Override
    public RevolverExecutorType getExecutorType() {
        return RevolverExecutorType.RESILIENCE;
    }

    @Override
    public void reload(RevolverConfig revolverConfig) {
        setupExecutor(revolverConfig.getResilienceConfig());
        ResilienceUtil.initializeResilience(revolverConfig, this);
    }

    public ExecutorService getExecutor() {
        if (executor == null) {
            setupExecutor(revolverConfigHolder.getConfig() != null
                    ? revolverConfigHolder.getConfig().getResilienceConfig()
                    : new ResilienceConfig());
        }
        return executor;
    }

    private void setupExecutor(ResilienceConfig resilienceConfig) {
        ThreadPoolConfig threadPoolConfig = resilienceConfig.getThreadPoolConfig();
        ThreadFactory threadFactory = new NamedThreadFactory(THREAD_POOL_PREFIX);
        this.executor = new ThreadPoolExecutor(threadPoolConfig.getCorePoolSize(), threadPoolConfig.getMaxPoolSize(),
                threadPoolConfig.getKeepAliveTimeInSeconds(), TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(threadPoolConfig.getQueueSize()), threadFactory);
    }
}
