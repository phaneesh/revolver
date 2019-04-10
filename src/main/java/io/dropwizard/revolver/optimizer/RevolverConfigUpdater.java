package io.dropwizard.revolver.optimizer;

import com.google.common.collect.Maps;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.LatencyConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConcurrencyConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerTimeoutConfig;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
        metricsCache.forEach((key, optimizerMetrics) -> {
            if(optimizerAggregatedMetricsMap.get(key.getName()) == null)
                optimizerAggregatedMetricsMap.put(key.getName(), OptimizerAggregatedMetrics.builder()
                        .pool(key.getName())
                        .metricsAggValueMap(Maps.newHashMap())
                        .build());

            OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap.get(key.getName());
            Map<String, Number> aggregatedMetricsValues = optimizerAggregatedMetrics.getMetricsAggValueMap();

            optimizerMetrics.getMetrics()
                    .forEach((metric, value) -> {
                        OptimizerMetrics.AggregationAlgo aggregationAlgo = optimizerMetrics.getAggregationAlgo();
                        switch (aggregationAlgo) {
                            case MAX:
                                if(aggregatedMetricsValues.get(metric) == null || aggregatedMetricsValues.get(metric)
                                                                                          .intValue() < value.intValue()) {
                                    log.error("Max Algo : from " + aggregatedMetricsValues.get(metric) + ", to : " + value);
                                    aggregatedMetricsValues.put(metric, value);
                                }
                                break;
                            case AVG:
                                if(aggregatedMetricsValues.get(metric) == null) {
                                    aggregatedMetricsValues.put(metric, value);
                                    log.error("Avg Algo for key : " + metric + "to : " + value);
                                } else {
                                    log.error("Avg Algo for key : " + metric + " from " + aggregatedMetricsValues.get(metric) + ", to : " +
                                              ((aggregatedMetricsValues.get(metric)
                                                        .intValue() + value.intValue()) >> 1));
                                    aggregatedMetricsValues.put(metric, (aggregatedMetricsValues.get(metric)
                                                                                 .intValue() + value.intValue()) >> 1);
                                }
                        }
                    });

        });
        updateRevolverConfig(optimizerAggregatedMetricsMap);

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

        //Not tuning the parameters for pools having maxActiveThreads <=3 and difference between concurrency and active threads is 1
        //Else, the concurrency will keep fluctuating between 2/3/4 at every round of optimisation
        if(maxRollingActiveThreads <= 3 && (concurrency - 1) <= maxRollingActiveThreads) {
            return;
        }
        if(maxRollingActiveThreads == 0) {
            threadPoolConfig.setConcurrency(1);
            log.error("Setting concurrency for : " + poolName + " from : " + concurrency + " to : " + threadPoolConfig.getConcurrency() +
                      ", maxRollingActiveThreads : " + maxRollingActiveThreads);
            return;
        }

        if(maxRollingActiveThreads > concurrency * concurrencyConfig.getMaxThreshold() ||
           maxRollingActiveThreads < concurrency * concurrencyConfig.getMinThreshold()) {

            int updatedConcurrency = (int)Math.ceil(maxRollingActiveThreads * concurrencyConfig.getBandwidth());
            threadPoolConfig.setConcurrency(updatedConcurrency);
            configUpdated.set(true);
            log.error("Setting concurrency for : " + poolName + " from : " + concurrency + " to : " + updatedConcurrency +
                      ", maxRollingActiveThreads : " + maxRollingActiveThreads);
        }

    }

    private void updateTimeoutSettings(ThreadPoolConfig threadPool, OptimizerAggregatedMetrics optimizerAggregatedMetrics,
                                       AtomicBoolean configUpdated, RevolverHttpApiConfig api) {

        OptimizerTimeoutConfig timeoutConfig = optimizerConfig.getTimeoutConfig();
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
        log.error("Setting timeout for : " + api.getApi() + " from : " + threadPool.getTimeout() + " to : " + newTimeout + ", " +
                  "meanTimeoutValue : " + meanTimeoutValue + ", with timeout buffer : " + timeoutBuffer);
        threadPool.setTimeout(newTimeout);

    }

    private void updateLatencySettings(RevolverHttpApiConfig api, OptimizerAggregatedMetrics optimizerAggregatedMetrics) {
        int meanLatency = optimizerAggregatedMetrics.getMetricsAggValueMap()
                                  .get(OptimizerUtils.LATENCY_PERCENTILE_50) ==
                          null ? 0 : optimizerAggregatedMetrics.getMetricsAggValueMap()
                                  .get(OptimizerUtils.LATENCY_PERCENTILE_50)
                                  .intValue();

        if(meanLatency <= 0) {
            return;
        }
        log.error("meanLatency : " + meanLatency + " for api : " + api);
        if(api.getLatencyConfig() == null) {
            api.setLatencyConfig(LatencyConfig.builder()
                                         .build());
        }
        api.getLatencyConfig()
                .setLatencyMetricValue(meanLatency);

    }
}
