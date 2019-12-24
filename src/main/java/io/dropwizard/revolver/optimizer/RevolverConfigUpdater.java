package io.dropwizard.revolver.optimizer;

import static io.dropwizard.revolver.optimizer.hystrix.metrics.ThreadPoolMetric.ROLLING_MAX_ACTIVE_THREADS;
import static io.dropwizard.revolver.optimizer.resilience.metrics.BulkheadMetric.BULKHEAD_AVAILABLE_CONCURRENT_CALLS;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.ApiLatencyConfig;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.core.resilience.ResilienceHttpContext;
import io.dropwizard.revolver.core.resilience.ResilienceUtil;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.optimizer.OptimalThreadPoolAttributes.OptimalThreadPoolAttributesBuilder;
import io.dropwizard.revolver.optimizer.OptimalTimeoutAttributes.OptimalTimeoutAttributesBuilder;
import io.dropwizard.revolver.optimizer.config.OptimizerConcurrencyConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerTimeConfig;
import io.dropwizard.revolver.optimizer.hystrix.metrics.AggregationAlgo;
import io.dropwizard.revolver.optimizer.hystrix.metrics.LatencyMetric;
import io.dropwizard.revolver.optimizer.resilience.metrics.BulkheadMetric;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
    private MetricRegistry metrics;
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

            // Map to keep latency metrics' sum and count at api level
            Map<String, Map<String, OptimizerAggregatedMetric>> aggregateApiLevelLatencyMetrics = Maps.newHashMap();

            metricsCache.forEach((key, optimizerMetrics) -> {
                optimizerMetrics.getMetrics().forEach((metric, value) -> {
                    aggregateAppLevelLatencyMetrics(aggregatedAppLatencyMetrics, metric, value);
                    aggregateApiLevelMetrics(apiLevelThreadPoolMetrics,
                            aggregateApiLevelLatencyMetrics, metric, value, key);
                });

            });

            Map<String, Number> appLevelLatencyMetrics = avgAppLevelLatencyMetrics(aggregatedAppLatencyMetrics);
            Map<String, OptimizerMetrics> apiLevelLatencyMetrics = avgApiLevelLatencyMetrics(
                    aggregateApiLevelLatencyMetrics);

            updateRevolverConfig(apiLevelThreadPoolMetrics, apiLevelLatencyMetrics);
            updateLatencyThreshold(appLevelLatencyMetrics);
        } catch (Exception e) {
            log.error("Revolver config couldn't be updated : " + e);
        }
    }

    private void updateRevolverConfig(Map<String, OptimizerMetrics> apiLevelThreadPoolMetrics,
            Map<String, OptimizerMetrics> apiLevelLatencyMetrics) {
        AtomicBoolean configUpdated = new AtomicBoolean();
        revolverConfig.getServices().forEach(revolverServiceConfig -> {
            if (revolverServiceConfig.getThreadPoolGroupConfig() != null) {
                revolverServiceConfig.getThreadPoolGroupConfig().getThreadPools()
                        .forEach(threadPoolConfig -> {
                            OptimizerMetrics optimizerThreadPoolMetrics = apiLevelThreadPoolMetrics
                                    .get(threadPoolConfig.getThreadPoolName());
                            updateConcurrencySettingForPool(threadPoolConfig, optimizerThreadPoolMetrics,
                                    configUpdated, threadPoolConfig.getThreadPoolName());
                        });
            }
            if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
                ((RevolverHttpServiceConfig) revolverServiceConfig).getApis().forEach(api -> {
                    updateApiSettings(api.getRuntime(), apiLevelThreadPoolMetrics, apiLevelLatencyMetrics,
                            configUpdated);
                });
            }
        });

        if (configUpdated.get()) {
            ResilienceUtil.initializeResilience(revolverConfig, new ResilienceHttpContext(), metrics);
            RevolverBundle.loadServiceConfiguration(revolverConfig);
        }
    }

    private void updateConcurrencySettingForPool(ThreadPoolConfig threadPoolConfig,
            OptimizerMetrics optimizerThreadPoolMetrics, AtomicBoolean configUpdated,
            String poolName) {

        OptimalThreadPoolAttributes optimalThreadPoolAttributes = calculateOptimalThreadPoolSize(
                threadPoolConfig.getConcurrency(), poolName,
                optimizerThreadPoolMetrics);
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
            Map<String, OptimizerMetrics> apiLevelLatencyMetrics, AtomicBoolean configUpdated) {

        String commandName = hystrixCommandConfig.getThreadPool().getThreadPoolName();
        OptimizerMetrics optimizerLatencyMetrics = apiLevelLatencyMetrics.get(commandName);
        OptimizerMetrics optimizerThreadPoolMetrics = apiLevelThreadPoolMetrics.get(commandName);

        if (optimizerLatencyMetrics == null || optimizerThreadPoolMetrics == null) {
            return;
        }
        updateConcurrencySettingForCommand(hystrixCommandConfig.getThreadPool(), optimizerThreadPoolMetrics,
                configUpdated, commandName);
        updateTimeoutSettingForCommand(hystrixCommandConfig.getThreadPool(), optimizerLatencyMetrics,
                configUpdated, commandName);
    }


    private void updateConcurrencySettingForCommand(ThreadPoolConfig threadPoolConfig,
            OptimizerMetrics optimizerThreadPoolMetrics, AtomicBoolean configUpdated,
            String poolName) {

        OptimalThreadPoolAttributes optimalThreadPoolAttributes = calculateOptimalThreadPoolSize(
                threadPoolConfig.getConcurrency(), poolName,
                optimizerThreadPoolMetrics);
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
        } else {
            // aggregate metric value into sum and update count of metric
            OptimizerAggregatedMetric optimizerAggregatedMetrics = aggregatedAppLatencyMetrics.get(metric);
            optimizerAggregatedMetrics.setSum(optimizerAggregatedMetrics.getSum()
                    + value.longValue());
            optimizerAggregatedMetrics.setCount(optimizerAggregatedMetrics.getCount() + 1L);
        }

        aggregatedAppLatencyMetrics.forEach((metricName, aggregatedAppMetrics) -> {
            log.info("Aggregated " + metricName + " for app: "
                    + aggregatedAppMetrics.getSum() / aggregatedAppMetrics.getCount());
        });
    }

    private void aggregateApiLevelMetrics(
            Map<String, OptimizerMetrics> apiLevelThreadPoolMetrics,
            Map<String, Map<String, OptimizerAggregatedMetric>> aggregateApiLevelLatencyMetrics,
            String metric, Number value, OptimizerCacheKey key) {
        AggregationAlgo aggregationAlgo = key.getMetricType().getAggregationAlgo();
        switch (aggregationAlgo) {
            case MAX:
                Map<String, Number> threadPoolMetricsMap = getThreadPoolMetricsMap(apiLevelThreadPoolMetrics, key);
                if (!threadPoolMetricsMap.containsKey(metric)
                        || threadPoolMetricsMap.get(metric).intValue() < value.intValue()) {
                    threadPoolMetricsMap.put(metric, value);
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

    private Map<String, Number> getThreadPoolMetricsMap(
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

    private OptimalThreadPoolAttributes calculateOptimalThreadPoolSize(int initialConcurrency, String poolName,
            OptimizerMetrics optimizerThreadPoolMetrics) {
        OptimalThreadPoolAttributesBuilder initialConcurrencyAttrBuilder = OptimalThreadPoolAttributes.builder()
                .optimalConcurrency(initialConcurrency);

        OptimizerConcurrencyConfig concurrencyConfig = optimizerConfig.getConcurrencyConfig();
        if (concurrencyConfig == null || !concurrencyConfig.isEnabled()
                || (!optimizerThreadPoolMetrics.getMetrics()
                .containsKey(ROLLING_MAX_ACTIVE_THREADS.getMetricName())
                && !optimizerThreadPoolMetrics.getMetrics()
                .containsKey(BULKHEAD_AVAILABLE_CONCURRENT_CALLS.getMetricName()))) {
            return initialConcurrencyAttrBuilder.build();
        }

        int maxRollingActiveThreads = calculateMaxRollingActiveThreads(initialConcurrency, optimizerThreadPoolMetrics);

        log.info("Optimizer Concurrency Settings Enabled : {}, "
                        + "Max Threads Multiplier : {}, Max Threshold : {}, Initial Concurrency : {}, Pool Name: {}",
                concurrencyConfig.isEnabled(), concurrencyConfig.getMaxThreadsMultiplier(),
                concurrencyConfig.getMaxThreshold(), initialConcurrency, poolName);

        if (maxRollingActiveThreads == 0) {
            return OptimalThreadPoolAttributes.builder()
                    .optimalConcurrency(DEFAULT_CONCURRENCY)
                    .maxRollingActiveThreads(maxRollingActiveThreads)
                    .build();
        } else if ((maxRollingActiveThreads > initialConcurrency * concurrencyConfig.getMaxThreshold()
                || maxRollingActiveThreads < initialConcurrency * concurrencyConfig.getMinThreshold())
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

    private int calculateMaxRollingActiveThreads(int initialConcurrency, OptimizerMetrics optimizerThreadPoolMetrics) {
        Number number = optimizerThreadPoolMetrics.getMetrics()
                .get(ROLLING_MAX_ACTIVE_THREADS.getMetricName());
        if (number == null) {
            number = initialConcurrency - optimizerThreadPoolMetrics.getMetrics()
                    .getOrDefault(BULKHEAD_AVAILABLE_CONCURRENT_CALLS.getMetricName(), initialConcurrency).intValue();
        }
        return number.intValue();
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

    private void updateRevolverConfig(
            Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap) {
        AtomicBoolean configUpdated = new AtomicBoolean();
        revolverConfig.getServices().forEach(revolverServiceConfig -> {
            if (revolverServiceConfig.getThreadPoolGroupConfig() != null) {
                revolverServiceConfig.getThreadPoolGroupConfig().getThreadPools()
                        .forEach(threadPoolConfig -> {
                            updatedPoolSettings(threadPoolConfig, optimizerAggregatedMetricsMap,
                                    configUpdated);
                        });
            }
            if (revolverServiceConfig instanceof RevolverHttpServiceConfig) {
                ((RevolverHttpServiceConfig) revolverServiceConfig).getApis().forEach(api -> {
                    updatedApiSettings(revolverServiceConfig, api, optimizerAggregatedMetricsMap,
                            configUpdated);
                });
            }
        });

        if (configUpdated.get()) {
            RevolverBundle.loadServiceConfiguration(revolverConfig);
        }
    }

    private void updatedPoolSettings(ThreadPoolConfig threadPoolConfig,
            Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap,
            AtomicBoolean configUpdated) {

        OptimizerConcurrencyConfig optimizerConcurrencyConfig = optimizerConfig
                .getConcurrencyConfig();
        if (optimizerConcurrencyConfig == null || !optimizerConfig.getConcurrencyConfig()
                .isEnabled()) {
            return;
        }
        OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap
                .get(threadPoolConfig.getThreadPoolName());

        if (optimizerAggregatedMetrics == null) {
            return;
        }
        updateConcurrencySetting(threadPoolConfig, optimizerAggregatedMetrics, configUpdated,
                threadPoolConfig.getThreadPoolName());

    }

    private void updatedApiSettings(RevolverServiceConfig revolverServiceConfig,
            RevolverHttpApiConfig api,
            Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap,
            AtomicBoolean configUpdated) {

        String key = revolverServiceConfig.getService() + "." + api.getApi();
        OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap
                .get(key);

        if (optimizerAggregatedMetrics == null) {
            return;
        }
        updateConcurrencySetting(api.getRuntime().getThreadPool(), optimizerAggregatedMetrics,
                configUpdated, api.getApi());
        updateTimeoutSettings(api.getRuntime().getThreadPool(), optimizerAggregatedMetrics,
                configUpdated, api);
        updateLatencySettings(api, optimizerAggregatedMetrics);
    }

    private void updateConcurrencySetting(ThreadPoolConfig threadPoolConfig,
            OptimizerAggregatedMetrics optimizerAggregatedMetrics, AtomicBoolean configUpdated,
            String poolName) {
        OptimizerConcurrencyConfig optimizerConcurrencyConfig = optimizerConfig
                .getConcurrencyConfig();
        if (optimizerConcurrencyConfig == null || !optimizerConfig.getConcurrencyConfig()
                .isEnabled()) {
            return;
        }
        if (optimizerAggregatedMetrics.getMetricsAggValueMap()
                .get(OptimizerUtils.ROLLING_MAX_ACTIVE_THREADS) == null) {
            return;
        }
        OptimizerConcurrencyConfig concurrencyConfig = optimizerConfig.getConcurrencyConfig();
        log.info("Enabled : {}, MaxThreadsMultiplier : {}, MaxThreshold : {}, Initial Concurrency : {}, Pool : {}",
                concurrencyConfig.isEnabled(), concurrencyConfig.getMaxThreadsMultiplier(),
                concurrencyConfig.getMaxThreshold(), threadPoolConfig.getInitialConcurrency(),
                threadPoolConfig.getThreadPoolName());
        int maxRollingActiveThreads = optimizerAggregatedMetrics.getMetricsAggValueMap()
                .get(OptimizerUtils.ROLLING_MAX_ACTIVE_THREADS).intValue();
        int concurrency = threadPoolConfig.getConcurrency();

        if (maxRollingActiveThreads == 0) {
            threadPoolConfig.setConcurrency(3);
            log.info("Setting concurrency for : " + poolName + " from : " + concurrency + " to : "
                    + threadPoolConfig.getConcurrency() + ", maxRollingActiveThreads : "
                    + maxRollingActiveThreads);
            return;
        }

        if ((maxRollingActiveThreads > concurrency * concurrencyConfig.getMaxThreshold()
                || maxRollingActiveThreads < concurrency * concurrencyConfig.getMinThreshold())
                && maxRollingActiveThreads
                < threadPoolConfig.getInitialConcurrency() * concurrencyConfig
                .getMaxThreadsMultiplier()) {

            int updatedConcurrency = (int) Math
                    .ceil(maxRollingActiveThreads * concurrencyConfig.getBandwidth());
            threadPoolConfig.setConcurrency(updatedConcurrency);
            configUpdated.set(true);
            log.info("Setting concurrency for : " + poolName + " from : " + concurrency + " to : "
                    + updatedConcurrency + ", maxRollingActiveThreads : "
                    + maxRollingActiveThreads);
        }

    }

    private void updateTimeoutSettings(ThreadPoolConfig threadPool,
            OptimizerAggregatedMetrics optimizerAggregatedMetrics, AtomicBoolean configUpdated,
            RevolverHttpApiConfig api) {

        OptimizerTimeConfig timeoutConfig = optimizerConfig.getTimeConfig();
        if (timeoutConfig == null || !timeoutConfig.isEnabled()
                || optimizerAggregatedMetrics.getMetricsAggValueMap()
                .get(timeoutConfig.getTimeoutMetric()) == null) {
            return;
        }
        int meanTimeoutValue = optimizerAggregatedMetrics.getMetricsAggValueMap()
                .get(timeoutConfig.getTimeoutMetric()).intValue();

        if (meanTimeoutValue <= 0) {
            return;
        }

        int currentTimeout = threadPool.getTimeout();
        int newTimeout = currentTimeout;

        Set<RevolverHttpApiConfig.RequestMethod> methods = api.getMethods();
        double timeoutBuffer;

        if (methods.isEmpty() || !(methods.contains(RevolverHttpApiConfig.RequestMethod.GET))) {
            timeoutBuffer = timeoutConfig.getAllMethodTimeoutBuffer();
        } else {
            timeoutBuffer = timeoutConfig.getGetMethodTimeoutBuffer();
        }

        if (currentTimeout < meanTimeoutValue || currentTimeout > (meanTimeoutValue
                * timeoutBuffer)) {
            newTimeout = (int) (meanTimeoutValue * timeoutBuffer);
            configUpdated.set(true);
        }
        log.info("Setting timeout for : " + api.getApi() + " from : " + threadPool.getTimeout()
                + " to : " + newTimeout + ", " + "meanTimeoutValue : " + meanTimeoutValue
                + ", with timeout buffer : " + timeoutBuffer);
        //threadPool.setTimeout(newTimeout);

    }

    private void updateLatencySettings(RevolverHttpApiConfig api,
            OptimizerAggregatedMetrics optimizerAggregatedMetrics) {
        OptimizerTimeConfig optimizerTimeConfig = optimizerConfig.getTimeConfig();
        if (optimizerTimeConfig == null || !optimizerTimeConfig.isEnabled()) {
            return;
        }
        String latencyMetric = optimizerTimeConfig.getApiLatencyMetric();
        int apiLatency =
                optimizerAggregatedMetrics.getMetricsAggValueMap().get(latencyMetric) == null ? 0
                        : optimizerAggregatedMetrics.getMetricsAggValueMap().get(latencyMetric)
                                .intValue();

        if (apiLatency <= 0) {
            return;
        }
        if (api.getApiLatencyConfig() == null) {
            api.setApiLatencyConfig(ApiLatencyConfig.builder().build());
        }
        api.getApiLatencyConfig().setLatency(apiLatency);

    }
}
