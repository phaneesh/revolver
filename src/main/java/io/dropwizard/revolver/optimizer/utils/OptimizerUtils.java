package io.dropwizard.revolver.optimizer.utils;

import com.google.common.collect.Lists;
import io.dropwizard.revolver.optimizer.config.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/***
 Created by nitish.goyal on 29/03/19
 ***/
public class OptimizerUtils {

    public static final String ROLLING_MAX_ACTIVE_THREADS = "rollingMaxActiveThreads";
    public static final String THREAD_POOL_PREFIX = "HystrixThreadPool";
    public static final String LATENCY_PERCENTILE_99 = "latencyExecute_percentile_99";
    public static final String LATENCY_PERCENTILE_50 = "latencyExecute_percentile_50";

    private static final List<String> METRICS_TO_READ = Lists.newArrayList("propertyValue_maximumSize", ROLLING_MAX_ACTIVE_THREADS);

    public static List<String> getMetricsToRead() {
        return Collections.unmodifiableList(METRICS_TO_READ);
    }

    public static OptimizerConfig getDefaultOptimizerConfig() {
        return OptimizerConfig.builder()
                .initialDelay(10)
                .timeUnit(TimeUnit.MINUTES)
                .concurrencyConfig(OptimizerConcurrencyConfig.builder()
                                           .bandwidth(1.4)
                                           .minThreshold(0.5)
                                           .maxThreshold(0.85)
                                           .build())
                .configUpdaterConfig(OptimizerConfigUpdaterConfig.builder()
                                             .repeatAfter(5)
                                             .timeUnit(TimeUnit.MINUTES)
                                             .build())
                .metricsCollectorConfig(OptimizerMetricsCollectorConfig.builder()
                                                .repeatAfter(30)
                                                .timeUnit(TimeUnit.SECONDS)
                                                .cachingWindowInMinutes(15)
                                                .concurrency(3)
                                                .build())
                .timeoutConfig(OptimizerTimeoutConfig.builder()
                                       .allMethodTimeoutBuffer(1.4)
                                       .getMethodTimeoutBuffer(1.2)
                                       .latencyMetrics(Lists.newArrayList(LATENCY_PERCENTILE_99, LATENCY_PERCENTILE_50))
                                       .timeoutMetric(LATENCY_PERCENTILE_99)
                                       .build())
                .enabled(true)
                .build();
    }
}
