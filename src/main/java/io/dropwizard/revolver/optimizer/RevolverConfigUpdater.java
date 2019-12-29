package io.dropwizard.revolver.optimizer;

import static io.dropwizard.revolver.optimizer.hystrix.metrics.ThreadPoolMetric.ROLLING_MAX_ACTIVE_THREADS;
import static io.dropwizard.revolver.optimizer.resilience.metrics.BulkheadMetric.BULKHEAD_AVAILABLE_CONCURRENT_CALLS;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.core.resilience.ResilienceHttpContext;
import io.dropwizard.revolver.core.resilience.ResilienceUtil;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.optimizer.OptimalThreadPoolAttributes.OptimalThreadPoolAttributesBuilder;
import io.dropwizard.revolver.optimizer.OptimalTimeoutAttributes.OptimalTimeoutAttributesBuilder;
import io.dropwizard.revolver.optimizer.config.OptimizerConcurrencyConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerTimeConfig;
import io.dropwizard.revolver.optimizer.hystrix.metrics.AggregationAlgo;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class RevolverConfigUpdater implements Runnable {

    private RevolverConfig revolverConfig;
    private OptimizerConfig optimizerConfig;
    private ResilienceHttpContext resilienceHttpContext;
    private OptimizerMetricsCache optimizerMetricsCache;
    private static final int DEFAULT_CONCURRENCY = 3;

    @Override
    public void run() {
        try {
            log.info("Running revolver config updater job with exception catching enabled");
            Map<OptimizerCacheKey, OptimizerMetrics> metricsCache = optimizerMetricsCache.getCache();
            if (metricsCache.isEmpty()) {
                log.info("Metrics cache is empty");
                return;
            }

            // Map to compute sum and keep count of all latency metrics :
            //       latencyExecute_percentile_995, latencyExecute_percentile_90,
            //       latencyExecute_percentile_75, latencyExecute_percentile_50
            Map<String, OptimizerAggregatedMetric> aggregatedAppLatencyMetrics = Maps.newHashMap();

            // Map to keep max values of thread pool metrics at api level
            Map<String, OptimizerMetrics> apiLevelThreadPoolMetrics = Maps.newHashMap();

            // Map to keep min values of bulkhead available concurrent calls metrics at api level
            Map<String, OptimizerMetrics> apiLevelBulkheadMetrics = Maps.newHashMap();

            // Map to keep latency metrics' sum and count at api level
            Map<String, Map<String, OptimizerAggregatedMetric>> aggregateApiLevelLatencyMetrics = Maps.newHashMap();

            metricsCache.forEach((key, optimizerMetrics) -> {
                optimizerMetrics.getMetrics().forEach((metric, value) -> {
                    aggregateAppLevelLatencyMetrics(aggregatedAppLatencyMetrics, metric, value);
                    aggregateApiLevelMetrics(apiLevelThreadPoolMetrics, apiLevelBulkheadMetrics,
                            aggregateApiLevelLatencyMetrics, metric, value, key);
                });

            });

            Map<String, Number> appLevelLatencyMetrics = avgAppLevelLatencyMetrics(aggregatedAppLatencyMetrics);
            Map<String, OptimizerMetrics> apiLevelLatencyMetrics = avgApiLevelLatencyMetrics(
                    aggregateApiLevelLatencyMetrics);

            updateRevolverConfig(apiLevelThreadPoolMetrics, apiLevelBulkheadMetrics, apiLevelLatencyMetrics);
            updateLatencyThreshold(appLevelLatencyMetrics);
        } catch (Exception e) {
            log.error("Revolver config couldn't be updated : " + e);
        }
    }

    private void updateRevolverConfig(Map<String, OptimizerMetrics> apiLevelThreadPoolMetrics,
            Map<String, OptimizerMetrics> apiLevelBulkheadMetrics,
            Map<String, OptimizerMetrics> apiLevelLatencyMetrics) {
        AtomicBoolean configUpdated = new AtomicBoolean();
        revolverConfig.getServices().forEach(revolverServiceConfig -> {
            if (revolverServiceConfig.getThreadPoolGroupConfig() != null) {
                revolverServiceConfig.getThreadPoolGroupConfig().getThreadPools()
                        .forEach(threadPoolConfig -> {
                            OptimizerMetrics optimizerThreadPoolMetrics = apiLevelThreadPoolMetrics
                                    .get(threadPoolConfig.getThreadPoolName());
                            OptimizerMetrics optimizerBulkheadMetrics = apiLevelBulkheadMetrics
                                    .get(threadPoolConfig.getThreadPoolName());
                            updateConcurrencySettingForPool(threadPoolConfig, optimizerThreadPoolMetrics,
                                    optimizerBulkheadMetrics, configUpdated, threadPoolConfig.getThreadPoolName());
                        });
            }
            if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
                ((RevolverHttpServiceConfig) revolverServiceConfig).getApis().forEach(api -> {
                    updateApiSettings(api.getRuntime(), apiLevelThreadPoolMetrics, apiLevelLatencyMetrics,
                            apiLevelBulkheadMetrics, configUpdated);
                });
            }
        });

        if (configUpdated.get()) {
            ResilienceUtil.initializeResilience(revolverConfig, resilienceHttpContext);
            RevolverBundle.loadServiceConfiguration(revolverConfig);
        }
    }

    private void updateConcurrencySettingForPool(ThreadPoolConfig threadPoolConfig,
            OptimizerMetrics optimizerThreadPoolMetrics, OptimizerMetrics optimizerBulkheadMetrics,
            AtomicBoolean configUpdated, String poolName) {

        OptimalThreadPoolAttributes optimalThreadPoolAttributes = calculateOptimalThreadPoolSize(
                threadPoolConfig.getConcurrency(), threadPoolConfig.getInitialConcurrency(), poolName,
                optimizerThreadPoolMetrics, optimizerBulkheadMetrics);
        if (optimalThreadPoolAttributes.getOptimalConcurrency() != threadPoolConfig.getConcurrency()) {
            log.info("Setting concurrency for pool : " + poolName + " from : " + threadPoolConfig.getConcurrency()
                    + " to : "
                    + optimalThreadPoolAttributes.getOptimalConcurrency() + ", maxRollingActiveThreads : "
                    + optimalThreadPoolAttributes.getMaxRollingActiveThreads());
            threadPoolConfig.setConcurrency(optimalThreadPoolAttributes.getOptimalConcurrency());
            configUpdated.set(true);
        }

    }

    private void updateApiSettings(HystrixCommandConfig hystrixCommandConfig,
            Map<String, OptimizerMetrics> apiLevelThreadPoolMetrics,
            Map<String, OptimizerMetrics> apiLevelBulkheadMetrics,
            Map<String, OptimizerMetrics> apiLevelLatencyMetrics, AtomicBoolean configUpdated) {

        String commandName = hystrixCommandConfig.getThreadPool().getThreadPoolName();
        OptimizerMetrics optimizerLatencyMetrics = apiLevelLatencyMetrics.get(commandName);
        OptimizerMetrics optimizerThreadPoolMetrics = apiLevelThreadPoolMetrics.get(commandName);
        OptimizerMetrics optimizerBulkheadMetrics = apiLevelBulkheadMetrics.get(commandName);
        if (optimizerLatencyMetrics == null ||
                (optimizerThreadPoolMetrics == null && optimizerBulkheadMetrics == null)) {
            return;
        }
        updateConcurrencySettingForCommand(hystrixCommandConfig.getThreadPool(), optimizerThreadPoolMetrics,
                optimizerBulkheadMetrics, configUpdated, commandName);
        updateTimeoutSettingForCommand(hystrixCommandConfig.getThreadPool(), optimizerLatencyMetrics,
                configUpdated, commandName);
    }


    private void updateConcurrencySettingForCommand(ThreadPoolConfig threadPoolConfig,
            OptimizerMetrics optimizerThreadPoolMetrics, OptimizerMetrics optimizerBulkheadMetrics,
            AtomicBoolean configUpdated,
            String poolName) {

        OptimalThreadPoolAttributes optimalThreadPoolAttributes = calculateOptimalThreadPoolSize(
                threadPoolConfig.getConcurrency(), threadPoolConfig.getInitialConcurrency(), poolName,
                optimizerThreadPoolMetrics, optimizerBulkheadMetrics);
        if (optimalThreadPoolAttributes.getOptimalConcurrency() != threadPoolConfig.getConcurrency()) {
            log.info("Setting concurrency for command : " + poolName + " from : " + threadPoolConfig.getConcurrency()
                    + " to : "
                    + optimalThreadPoolAttributes.getOptimalConcurrency() + ", maxRollingActiveThreads : "
                    + optimalThreadPoolAttributes.getMaxRollingActiveThreads());
            threadPoolConfig.setConcurrency(optimalThreadPoolAttributes.getOptimalConcurrency());
            configUpdated.set(true);
        }

    }


    private void updateTimeoutSettingForCommand(ThreadPoolConfig threadPoolConfig,
            OptimizerMetrics optimizerLatencyMetrics, AtomicBoolean configUpdated,
            String commandName) {

        OptimalTimeoutAttributes optimalTimeoutAttributes = calculateOptimalTimeout(threadPoolConfig.getTimeout(),
                optimizerLatencyMetrics);
        if (optimalTimeoutAttributes.getOptimalTimeout() != threadPoolConfig.getTimeout()) {
            threadPoolConfig.setTimeout(optimalTimeoutAttributes.getOptimalTimeout());
            configUpdated.set(true);
            log.info("Setting timeout for : " + commandName + " from : " + threadPoolConfig.getTimeout()
                    + " to : " + optimalTimeoutAttributes.getOptimalTimeout() + ", " + "meanTimeoutValue : " +
                    optimalTimeoutAttributes.getMeanTimeout()
                    + ", with timeout buffer : " + optimalTimeoutAttributes.getTimeoutBuffer());
        }

    }

    private OptimalTimeoutAttributes calculateOptimalTimeout(int currentTimeout,
            OptimizerMetrics optimizerLatencyMetrics) {
        OptimalTimeoutAttributesBuilder initialTimeoutAttributesBuilder = OptimalTimeoutAttributes
                .builder()
                .optimalTimeout(currentTimeout);

        OptimizerTimeConfig timeoutConfig = optimizerConfig.getTimeConfig();
        if (timeoutConfig == null || !timeoutConfig.isEnabled()
                || !optimizerLatencyMetrics.getMetrics()
                .containsKey(timeoutConfig.getTimeoutMetric())) {
            return initialTimeoutAttributesBuilder.build();
        }

        int meanTimeoutValue = optimizerLatencyMetrics.getMetrics()
                .get(timeoutConfig.getTimeoutMetric()).intValue();

        if (meanTimeoutValue <= 0) {
            return initialTimeoutAttributesBuilder
                    .meanTimeout(meanTimeoutValue)
                    .build();
        }

        double timeoutBuffer = timeoutConfig.getAllMethodTimeoutBuffer();

        if (currentTimeout < meanTimeoutValue || currentTimeout > (meanTimeoutValue * timeoutBuffer)) {
            return OptimalTimeoutAttributes.builder()
                    .meanTimeout(meanTimeoutValue)
                    .timeoutBuffer(timeoutBuffer)
                    .optimalTimeout((int) (meanTimeoutValue * timeoutBuffer))
                    .build();
        } else {
            return initialTimeoutAttributesBuilder
                    .meanTimeout(meanTimeoutValue)
                    .timeoutBuffer(timeoutBuffer)
                    .build();
        }

    }

    private void aggregateAppLevelLatencyMetrics(
            Map<String, OptimizerAggregatedMetric> aggregatedAppLatencyMetrics,
            String metric, Number value) {

        OptimizerTimeConfig optimizerTimeConfig = optimizerConfig.getTimeConfig();
        if (optimizerTimeConfig == null || !optimizerTimeConfig.isEnabled()
                || !optimizerTimeConfig.getLatencyMetrics().contains(metric)
                || value.intValue() == 0) {
            return;
        }

        if (!aggregatedAppLatencyMetrics.containsKey(metric)) {
            aggregatedAppLatencyMetrics.put(metric, OptimizerAggregatedMetric.builder()
                    .sum(0L)
                    .count(0L)
                    .build());
        }

        // aggregate metric value into sum and update count of metric
        OptimizerAggregatedMetric optimizerAggregatedMetrics = aggregatedAppLatencyMetrics.get(metric);
        optimizerAggregatedMetrics.setSum(optimizerAggregatedMetrics.getSum()
                + value.longValue());
        optimizerAggregatedMetrics.setCount(optimizerAggregatedMetrics.getCount() + 1L);

        aggregatedAppLatencyMetrics.forEach((metricName, aggregatedAppMetrics) -> {
            log.info("Aggregated " + metricName + " for app: "
                    + aggregatedAppMetrics.getSum() / aggregatedAppMetrics.getCount());
        });
    }

    private void aggregateApiLevelMetrics(
            Map<String, OptimizerMetrics> apiLevelThreadPoolMetrics,
            Map<String, OptimizerMetrics> apiLevelBulkheadMetrics,
            Map<String, Map<String, OptimizerAggregatedMetric>> aggregateApiLevelLatencyMetrics,
            String metric, Number value, OptimizerCacheKey key) {
        AggregationAlgo aggregationAlgo = key.getMetricType().getAggregationAlgo();
        switch (aggregationAlgo) {
            case MAX:
                Map<String, Number> threadPoolMetricsMap = getNullSafeOptimizerMetricsMap(apiLevelThreadPoolMetrics,
                        key);
                if (!threadPoolMetricsMap.containsKey(metric)
                        || threadPoolMetricsMap.get(metric).intValue() < value.intValue()) {
                    threadPoolMetricsMap.put(metric, value);
                }
                break;
            case MIN:
                Map<String, Number> bulkheadMetricsMap = getNullSafeOptimizerMetricsMap(apiLevelBulkheadMetrics, key);
                if (!bulkheadMetricsMap.containsKey(metric)
                        || bulkheadMetricsMap.get(metric).intValue() > value.intValue()) {
                    bulkheadMetricsMap.put(metric, value);
                }
                break;
            case AVG:
                OptimizerAggregatedMetric optimizerAggregatedMetric =
                        getAggregateMetricsMap(aggregateApiLevelLatencyMetrics, key, metric);
                optimizerAggregatedMetric.setSum(optimizerAggregatedMetric.getSum() + value.longValue());
                optimizerAggregatedMetric.setCount(optimizerAggregatedMetric.getCount() + 1);
                break;
        }
    }

    private Map<String, Number> getNullSafeOptimizerMetricsMap(
            Map<String, OptimizerMetrics> maxedThreadPoolMetrics, OptimizerCacheKey key) {
        if (!maxedThreadPoolMetrics.containsKey(key.getName())) {
            maxedThreadPoolMetrics.put(key.getName(),
                    OptimizerMetrics.builder().metrics(Maps.newHashMap())
                            .build());
        }

        OptimizerMetrics optimizerAggregatedMetrics = maxedThreadPoolMetrics
                .get(key.getName());

        return optimizerAggregatedMetrics.getMetrics();
    }

    private OptimizerAggregatedMetric getAggregateMetricsMap(
            Map<String, Map<String, OptimizerAggregatedMetric>> aggregatedMetrics, OptimizerCacheKey key,
            String metric) {
        if (!aggregatedMetrics.containsKey(key.getName())) {
            aggregatedMetrics.put(key.getName(), Maps.newHashMap());
        }

        if (!aggregatedMetrics.get(key.getName()).containsKey(metric)) {
            aggregatedMetrics.get(key.getName()).put(metric,
                    OptimizerAggregatedMetric.builder()
                            .sum(0L)
                            .count(0L)
                            .build());
        }

        return aggregatedMetrics.get(key.getName()).get(metric);
    }


    private Map<String, Number> avgAppLevelLatencyMetrics(
            Map<String, OptimizerAggregatedMetric> overallAppLatencyMetrics) {
        Map<String, Number> aggregatedAppLevelLatencyMetrics = Maps.newHashMap();
        overallAppLatencyMetrics.forEach((metricName, aggregatedAppMetrics) -> {
            aggregatedAppLevelLatencyMetrics
                    .put(metricName, aggregatedAppMetrics.getSum() / aggregatedAppMetrics.getCount());
        });
        return aggregatedAppLevelLatencyMetrics;
    }

    private Map<String, OptimizerMetrics> avgApiLevelLatencyMetrics(
            Map<String, Map<String, OptimizerAggregatedMetric>> aggregatedLatencyMetrics) {
        Map<String, OptimizerMetrics> aggregateApiLevelLatencyMetrics = Maps.newHashMap();
        aggregatedLatencyMetrics.forEach((keyName, latencyMetricMap) -> {
            if (!aggregateApiLevelLatencyMetrics.containsKey(keyName)) {
                aggregateApiLevelLatencyMetrics.put(keyName, OptimizerMetrics.builder()
                        .metrics(Maps.newHashMap())
                        .build());
            }
            latencyMetricMap.forEach((metric, aggregateMetric) -> {
                aggregateApiLevelLatencyMetrics
                        .get(keyName)
                        .getMetrics()
                        .put(metric, aggregateMetric.getSum() / aggregateMetric.getCount());
            });
        });
        return aggregateApiLevelLatencyMetrics;
    }

    private OptimalThreadPoolAttributes calculateOptimalThreadPoolSize(int currentConcurrency, int initialConcurrency,
            String poolName, OptimizerMetrics optimizerThreadPoolMetrics, OptimizerMetrics optimizerBulkheadMetrics) {
        OptimalThreadPoolAttributesBuilder initialConcurrencyAttrBuilder = OptimalThreadPoolAttributes.builder()
                .optimalConcurrency(currentConcurrency);

        OptimizerConcurrencyConfig concurrencyConfig = optimizerConfig.getConcurrencyConfig();
        if (concurrencyConfig == null || !concurrencyConfig.isEnabled()
                || (
                (optimizerThreadPoolMetrics == null || !optimizerThreadPoolMetrics.getMetrics()
                        .containsKey(ROLLING_MAX_ACTIVE_THREADS.getMetricName()))
                        && (optimizerBulkheadMetrics == null || !optimizerBulkheadMetrics.getMetrics()
                        .containsKey(BULKHEAD_AVAILABLE_CONCURRENT_CALLS.getMetricName())))
        ) {
            return initialConcurrencyAttrBuilder.build();
        }

        int maxRollingActiveThreads = calculateMaxRollingActiveThreads(currentConcurrency, optimizerThreadPoolMetrics,
                optimizerBulkheadMetrics);

        log.info("Optimizer Concurrency Settings Enabled : {}, "
                        + "Max Threads Multiplier : {}, Max Threshold : {}, Initial Concurrency : {}, Pool Name: {}",
                concurrencyConfig.isEnabled(), concurrencyConfig.getMaxThreadsMultiplier(),
                concurrencyConfig.getMaxThreshold(), currentConcurrency, poolName);

        if (maxRollingActiveThreads == 0) {
            return OptimalThreadPoolAttributes.builder()
                    .optimalConcurrency(DEFAULT_CONCURRENCY)
                    .maxRollingActiveThreads(maxRollingActiveThreads)
                    .build();
        } else if ((maxRollingActiveThreads > currentConcurrency * concurrencyConfig.getMaxThreshold()
                || maxRollingActiveThreads < currentConcurrency * concurrencyConfig.getMinThreshold())
                && maxRollingActiveThreads
                < initialConcurrency * concurrencyConfig.getMaxThreadsMultiplier()) {
            int optimalConcurrency = (int) Math
                    .ceil(maxRollingActiveThreads * concurrencyConfig.getBandwidth());
            return OptimalThreadPoolAttributes.builder()
                    .optimalConcurrency(optimalConcurrency)
                    .maxRollingActiveThreads(maxRollingActiveThreads)
                    .build();
        } else {
            return initialConcurrencyAttrBuilder
                    .maxRollingActiveThreads(maxRollingActiveThreads)
                    .build();
        }
    }

    private int calculateMaxRollingActiveThreads(int currentConcurrency, OptimizerMetrics optimizerThreadPoolMetrics,
            OptimizerMetrics optimizerBulkheadMetrics) {
        int hystrixMaxActiveThreads = optimizerThreadPoolMetrics != null
                ? optimizerThreadPoolMetrics.getMetrics()
                .getOrDefault(ROLLING_MAX_ACTIVE_THREADS.getMetricName(), new AtomicInteger(0)).intValue()
                : 0;

        int bulkheadActiveCalls = optimizerBulkheadMetrics != null
                ? (currentConcurrency - optimizerBulkheadMetrics.getMetrics()
                .getOrDefault(BULKHEAD_AVAILABLE_CONCURRENT_CALLS.getMetricName(),
                        new AtomicInteger(currentConcurrency)).intValue())
                : 0;

        return Math.max(hystrixMaxActiveThreads, bulkheadActiveCalls);
    }


    private void updateLatencyThreshold(Map<String, Number> aggregatedAppLevelMetricsValues) {

        OptimizerTimeConfig optimizerTimeConfig = optimizerConfig.getTimeConfig();
        if (optimizerTimeConfig == null || !optimizerTimeConfig.isEnabled()
                || aggregatedAppLevelMetricsValues
                .get(optimizerTimeConfig.getAppLatencyMetric()) == null) {
            return;
        }
        int latencyThresholdValue = aggregatedAppLevelMetricsValues
                .get(optimizerTimeConfig.getAppLatencyMetric()).intValue();
        optimizerTimeConfig.setAppLatencyThresholdValue(latencyThresholdValue);
    }

}
