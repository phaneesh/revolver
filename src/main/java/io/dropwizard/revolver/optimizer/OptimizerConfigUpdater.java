package io.dropwizard.revolver.optimizer;

import com.google.common.collect.Maps;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConcurrencyConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.Tuple;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class OptimizerConfigUpdater implements Runnable {

    private RevolverConfig revolverConfig;
    private OptimizerConfig optimizerConfig;
    private OptimizerMetricsCache optimizerMetricsCache;

    @Override
    public void run() {

        Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap = Maps.newHashMap();
        Map<Tuple<Long, String>, OptimizerMetrics> metricsCache = optimizerMetricsCache.getCache();
        if(metricsCache.isEmpty()) {
            return;
        }
        metricsCache.forEach((tuple, optimizerMetrics) -> {
            if(optimizerAggregatedMetricsMap.get(tuple.second()) == null)
                optimizerAggregatedMetricsMap.put(tuple.second(), OptimizerAggregatedMetrics.builder()
                        .pool(tuple.second())
                        .metricsMaxValueMap(Maps.newHashMap())
                        .build());

            OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap.get(tuple.second());
            Map<String, Number> aggregatedMetricsValues = optimizerAggregatedMetrics.getMetricsMaxValueMap();

            optimizerMetrics.getMetrics()
                    .forEach((metric, value) -> {
                        if(aggregatedMetricsValues.get(metric) == null || aggregatedMetricsValues.get(metric)
                                                                                  .intValue() < value.intValue()) {
                            aggregatedMetricsValues.put(metric, value);
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
        updateConcurrencySetting(threadPoolConfig, optimizerAggregatedMetrics, configUpdated);

    }

    private void updatedApiSettings(RevolverServiceConfig revolverServiceConfig, RevolverHttpApiConfig api,
                                    Map<String, OptimizerAggregatedMetrics> optimizerAggregatedMetricsMap, AtomicBoolean configUpdated) {

        String key = revolverServiceConfig.getService() + "." + api.getApi();
        OptimizerAggregatedMetrics optimizerAggregatedMetrics = optimizerAggregatedMetricsMap.get(key);

        if(optimizerAggregatedMetrics == null) {
            return;
        }
        updateConcurrencySetting(api.getRuntime()
                                         .getThreadPool(), optimizerAggregatedMetrics, configUpdated);

    }

    private void updateConcurrencySetting(ThreadPoolConfig threadPoolConfig, OptimizerAggregatedMetrics optimizerAggregatedMetrics,
                                          AtomicBoolean configUpdated) {
        if(optimizerAggregatedMetrics.getMetricsMaxValueMap()
                   .get(OptimizerUtils.ROLLING_MAX_ACTIVE_THREADS) == null) {
            return;
        }
        OptimizerConcurrencyConfig concurrencyConfig = optimizerConfig.getConcurrencyConfig();
        int maxRollingActiveThreads = optimizerAggregatedMetrics.getMetricsMaxValueMap()
                .get(OptimizerUtils.ROLLING_MAX_ACTIVE_THREADS)
                .intValue();
        int concurrency = threadPoolConfig.getConcurrency();

        if(maxRollingActiveThreads > concurrency * concurrencyConfig.getMaxThreshold()) {
            concurrency = (int)Math.ceil(concurrency * concurrencyConfig.getIncreaseBy());
            configUpdated.set(true);
        } else if(maxRollingActiveThreads < concurrency * concurrencyConfig.getMinThreshold()) {
            concurrency = (int)(concurrency * concurrencyConfig.getDecreaseBy());
            if(concurrency <= 0) {
                return;
            }
            configUpdated.set(true);
        }
        threadPoolConfig.setConcurrency(concurrency);
    }
}
