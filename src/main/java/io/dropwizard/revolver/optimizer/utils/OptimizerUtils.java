package io.dropwizard.revolver.optimizer.utils;

import com.google.common.collect.Lists;
import io.dropwizard.revolver.optimizer.config.OptimizerConcurrencyConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConfigUpdaterConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerMetricsCollectorConfig;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/***
 Created by nitish.goyal on 29/03/19
 ***/
public class OptimizerUtils {

    public static final String ROLLING_MAX_ACTIVE_THREADS = "rollingMaxActiveThreads";
    public static final String THREAD_POOL_PREFIX = "HystrixThreadPool";

    private static final List<String> METRICS_TO_READ = Lists.newArrayList("propertyValue_maximumSize", ROLLING_MAX_ACTIVE_THREADS);

    public static List<String> getMetricsToRead() {
        return Collections.unmodifiableList(METRICS_TO_READ);
    }

    public static OptimizerConfig getDefaultOptimizerConfig() {
        return OptimizerConfig.builder().initialDelay(5).timeUnit(TimeUnit.MINUTES).concurrencyConfig
                (OptimizerConcurrencyConfig.builder().minThreshold(0.6).maxThreshold(0.7).build())
                .configUpdaterConfig(OptimizerConfigUpdaterConfig.builder().repeatAfter(5).build()).metricsCollectorConfig
                        (OptimizerMetricsCollectorConfig.builder().repeatAfter(1).timeUnit(TimeUnit.MINUTES).cachingWindow(15)
                                 .concurrency(3).build())
                .enabled(true)
                .build();
    }
}
