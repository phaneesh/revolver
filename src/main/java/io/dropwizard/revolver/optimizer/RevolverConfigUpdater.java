package io.dropwizard.revolver.optimizer;

import com.google.common.collect.Maps;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.ApiLatencyConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConcurrencyConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerTimeConfig;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private OptimizerMetricsCache optimizerMetricsCache;

    @Override
    public void run() {

        Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap = Maps.newHashMap();
        Map<OptimizerCacheKey, OptimizerMetrics> metricsCache = optimizerMetricsCache.getCache();
        if(metricsCache.isEmpty()) {
            return;
        }

        Map<String, Integer> keyVsMetricCount = Maps.newHashMap();
        AtomicLong metricsCount = new AtomicLong(0);
        Map<String, Number> aggregatedAppLevelMetricsValues = Maps.newHashMap();

        metricsCache.forEach((key, optimizerMetrics) -> {
            if(optimizerAggregatedMetricsMap.get(key.getName()) == null)
                optimizerAggregatedMetricsMap.put(key.getName(), OptimizerAggregatedMetrics.builder()
                        .metricsAggValueMap(Maps.newHashMap())
                        .build());

            OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap.get(key.getName());
            Map<String, Number> aggregatedMetricsValues = optimizerAggregatedMetrics.getMetricsAggValueMap();

            optimizerMetrics.getMetrics()
                    .forEach((metric, value) -> {
                        aggregateAppLevelMetrics(aggregatedAppLevelMetricsValues, metric, value, metricsCount);
                        aggregateApiLevelMetrics(optimizerMetrics, aggregatedMetricsValues, metric, value, keyVsMetricCount, key);
                    });

        });

        updateAvgOfMetrics(keyVsMetricCount, optimizerAggregatedMetricsMap, aggregatedAppLevelMetricsValues, metricsCount);

        updateRevolverConfig(optimizerAggregatedMetricsMap);
        updateLatencyThreshold(aggregatedAppLevelMetricsValues);

    }

    private void updateAvgOfMetrics(Map<String, Integer> keyVsMetricCount,
                                    Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap,
                                    Map<String, Number> aggregatedAppLevelMetricsValues, AtomicLong metricsCount) {

        keyVsMetricCount.forEach((k, v) -> {
            OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap.get(k);
            optimizerAggregatedMetrics.getMetricsAggValueMap()
                    .forEach((metric, aggregatedValue) -> {
                        if(v != 0) {
                            optimizerAggregatedMetrics.getMetricsAggValueMap()
                                    .put(metric, aggregatedValue.intValue() / v);
                        }
                    });
        });

        aggregatedAppLevelMetricsValues.forEach((k, v) -> {
            aggregatedAppLevelMetricsValues.put(k, v.intValue() / metricsCount.get());
        });
    }

    private void updateLatencyThreshold(Map<String, Number> aggregatedAppLevelMetricsValues) {

        if(optimizerConfig.getTimeConfig() == null || aggregatedAppLevelMetricsValues.get(optimizerConfig.getTimeConfig()
                                                                                                  .getAppLatencyMetric()) == null) {
            return;
        }
        int latencyThresholdValue = aggregatedAppLevelMetricsValues.get(optimizerConfig.getTimeConfig()
                                                                                .getAppLatencyMetric())
                .intValue();
        optimizerConfig.getTimeConfig()
                .setAppLatencyThresholdValue(latencyThresholdValue);
    }

    private void aggregateAppLevelMetrics(Map<String, Number> aggregatedAppLevelMetricsValues, String metric, Number value,
                                          AtomicLong metricsCount) {

        if(!optimizerConfig.getTimeConfig()
                .getAppLatencyMetric()
                .equals(metric) || value.intValue() == 0) {
            return;
        }
        metricsCount.addAndGet(1);
        if(aggregatedAppLevelMetricsValues.get(metric) == null) {
            aggregatedAppLevelMetricsValues.put(metric, value);
        } else {
            aggregatedAppLevelMetricsValues.put(metric, (aggregatedAppLevelMetricsValues.get(metric)
                                                                 .intValue() + value.intValue()));
        }
    }

    private void aggregateApiLevelMetrics(OptimizerMetrics optimizerMetrics, Map<String, Number> aggregatedMetricsValues, String metric,
                                          Number value, Map<String, Integer> keyVsMetricCount, OptimizerCacheKey key) {
        OptimizerMetrics.AggregationAlgo aggregationAlgo = optimizerMetrics.getAggregationAlgo();
        switch (aggregationAlgo) {
            case MAX:
                if(aggregatedMetricsValues.get(metric) == null || aggregatedMetricsValues.get(metric)
                                                                          .intValue() < value.intValue()) {
                    aggregatedMetricsValues.put(metric, value);
                }
                break;
            case AVG:
                keyVsMetricCount.putIfAbsent(key.getName(), 0);
                if(aggregatedMetricsValues.get(metric) == null) {
                    aggregatedMetricsValues.put(metric, value);
                    keyVsMetricCount.put(key.getName(), 1);
                } else {
                    keyVsMetricCount.put(key.getName(), keyVsMetricCount.get(key.getName()) + 1);
                    aggregatedMetricsValues.put(metric, (aggregatedMetricsValues.get(metric)
                                                                 .intValue() + value.intValue()));
                }
        }
    }

    private void updateRevolverConfig(Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap) {
        AtomicBoolean configUpdated = new AtomicBoolean();
        revolverConfig.getServices()
                .forEach(revolverServiceConfig -> {
                    if(revolverServiceConfig.getThreadPoolGroupConfig() != null) {
                        revolverServiceConfig.getThreadPoolGroupConfig()
                                .getThreadPools()
                                .forEach(threadPoolConfig -> {
                                    updatedPoolSettings(threadPoolConfig, optimizerAggregatedMetricsMap, configUpdated);
                                });
                    }
                    if(revolverServiceConfig instanceof RevolverHttpServiceConfig) {
                        ((RevolverHttpServiceConfig)revolverServiceConfig).getApis()
                                .forEach(api -> {
                                    updatedApiSettings(revolverServiceConfig, api, optimizerAggregatedMetricsMap, configUpdated);
                                });
                    }
                });


        if(configUpdated.get()) {
            RevolverBundle.loadServiceConfiguration(revolverConfig);
        }
    }

    private void updatedPoolSettings(ThreadPoolConfig threadPoolConfig,
                                     Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap, AtomicBoolean configUpdated) {

        OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap.get(threadPoolConfig.getThreadPoolName());

        if(optimizerAggregatedMetrics == null) {
            return;
        }
        updateConcurrencySetting(threadPoolConfig, optimizerAggregatedMetrics, configUpdated, threadPoolConfig.getThreadPoolName());

    }

    private void updatedApiSettings(RevolverServiceConfig revolverServiceConfig, RevolverHttpApiConfig api,
                                    Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap, AtomicBoolean configUpdated) {

        String key = revolverServiceConfig.getService() + "." + api.getApi();
        OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap.get(key);

        if(optimizerAggregatedMetrics == null) {
            return;
        }
        updateConcurrencySetting(api.getRuntime()
                                         .getThreadPool(), optimizerAggregatedMetrics, configUpdated, api.getApi());
        updateTimeoutSettings(api.getRuntime()
                                      .getThreadPool(), optimizerAggregatedMetrics, configUpdated, api);

        updateLatencySettings(api, optimizerAggregatedMetrics);
    }

    private void updateConcurrencySetting(ThreadPoolConfig threadPoolConfig, OptimizerAggregatedMetrics optimizerAggregatedMetrics,
                                          AtomicBoolean configUpdated, String poolName) {
        if(optimizerAggregatedMetrics.getMetricsAggValueMap()
                   .get(OptimizerUtils.ROLLING_MAX_ACTIVE_THREADS) == null) {
            return;
        }
        OptimizerConcurrencyConfig concurrencyConfig = optimizerConfig.getConcurrencyConfig();
        int maxRollingActiveThreads = optimizerAggregatedMetrics.getMetricsAggValueMap()
                .get(OptimizerUtils.ROLLING_MAX_ACTIVE_THREADS)
                .intValue();
        int concurrency = threadPoolConfig.getConcurrency();

        if(maxRollingActiveThreads == 0) {
            threadPoolConfig.setConcurrency(3);
            return;
        }

        if(maxRollingActiveThreads > concurrency * concurrencyConfig.getMaxThreshold() ||
           maxRollingActiveThreads < concurrency * concurrencyConfig.getMinThreshold()) {

            int updatedConcurrency = (int)Math.ceil(maxRollingActiveThreads * concurrencyConfig.getBandwidth());
            threadPoolConfig.setConcurrency(updatedConcurrency);
            configUpdated.set(true);
        }

    }

    private void updateTimeoutSettings(ThreadPoolConfig threadPool, OptimizerAggregatedMetrics optimizerAggregatedMetrics,
                                       AtomicBoolean configUpdated, RevolverHttpApiConfig api) {

        OptimizerTimeConfig timeoutConfig = optimizerConfig.getTimeConfig();
        if(timeoutConfig == null || optimizerAggregatedMetrics.getMetricsAggValueMap()
                                            .get(timeoutConfig.getTimeoutMetric()) == null) {
            return;
        }
        int meanTimeoutValue = optimizerAggregatedMetrics.getMetricsAggValueMap()
                .get(timeoutConfig.getTimeoutMetric())
                .intValue();

        if(meanTimeoutValue <= 0) {
            return;
        }

        int currentTimeout = threadPool.getTimeout();
        int newTimeout = currentTimeout;

        Set<RevolverHttpApiConfig.RequestMethod> methods = api.getMethods();
        double timeoutBuffer;

        if(methods.isEmpty() || !(methods.contains(RevolverHttpApiConfig.RequestMethod.GET))) {
            timeoutBuffer = timeoutConfig.getAllMethodTimeoutBuffer();
        } else {
            timeoutBuffer = timeoutConfig.getGetMethodTimeoutBuffer();
        }

        if(currentTimeout < meanTimeoutValue) {
            newTimeout = (int)(meanTimeoutValue * timeoutBuffer);
            configUpdated.set(true);
        } else if(currentTimeout > (meanTimeoutValue * timeoutBuffer)) {
            newTimeout = (int)(meanTimeoutValue * timeoutBuffer);
            configUpdated.set(true);
        }
        threadPool.setTimeout(newTimeout);

    }

    private void updateLatencySettings(RevolverHttpApiConfig api, OptimizerAggregatedMetrics optimizerAggregatedMetrics) {
        String latencyMetric = optimizerConfig.getTimeConfig()
                .getApiLatencyMetric();
        int apiLatency = optimizerAggregatedMetrics.getMetricsAggValueMap()
                                 .get(latencyMetric) == null ? 0 : optimizerAggregatedMetrics.getMetricsAggValueMap()
                                 .get(latencyMetric)
                                 .intValue();

        if(apiLatency <= 0) {
            return;
        }
        if(api.getApiLatencyConfig() == null) {
            api.setApiLatencyConfig(ApiLatencyConfig.builder()
                                            .build());
        }
        api.getApiLatencyConfig()
                .setLatency(apiLatency);

    }
}
