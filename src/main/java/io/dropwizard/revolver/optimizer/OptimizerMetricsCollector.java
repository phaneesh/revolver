package io.dropwizard.revolver.optimizer;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerTimeConfig;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import java.util.SortedMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Slf4j
@Builder
@AllArgsConstructor
@Data
public class OptimizerMetricsCollector implements Runnable {

    private MetricRegistry metrics;
    private OptimizerMetricsCache optimizerMetricsCache;
    private OptimizerConfig optimizerConfig;

    @Override
    public void run() {

        log.info("Running optimiser metrics collection job");
        SortedMap<String, Gauge> gauges = metrics.getGauges();
        Long time = System.currentTimeMillis();

        try {
            captureThreadPoolMetrics(gauges, time);
            captureTimeMetrics(gauges, time);
        } catch (Exception e) {
            log.error("Error occurred while executing metrics collector");
        }
    }


    private void captureThreadPoolMetrics(SortedMap<String, Gauge> gauges, Long time) {
        gauges.forEach((k, v) -> {
            String[] splits = k.split("\\.");
            if (splits.length < 3) {
                return;
            }
            int length = splits.length;
            String metricName = splits[length - 1];
            if (!(OptimizerUtils.getMetricsToRead().contains(metricName)) || !((v
                    .getValue() instanceof Number))) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            String delimiter = "";
            for (int i = 1; i < length - 1; i++) {
                sb.append(delimiter);
                sb.append(splits[i]);
                delimiter = ".";
            }
            OptimizerCacheKey key = new OptimizerCacheKey(time, sb.toString());
            if (optimizerMetricsCache.get(key) == null) {
                optimizerMetricsCache.put(key, OptimizerMetrics.builder().metrics(Maps.newHashMap())
                        .aggregationAlgo(OptimizerMetrics.AggregationAlgo.MAX).build());
            }
            OptimizerMetrics optimizerMetrics = optimizerMetricsCache.get(key);
            if (optimizerMetrics == null) {
                return;
            }
            optimizerMetrics.getMetrics().put(metricName, (Number) v.getValue());

        });
    }

    private void captureTimeMetrics(SortedMap<String, Gauge> gauges, Long time) {
        OptimizerTimeConfig timeConfig = optimizerConfig.getTimeConfig();
        gauges.forEach((k, v) -> {
            if (timeConfig == null || !timeConfig.isEnabled()) {
                return;
            }
            String[] splits = k.split("\\.");
            if (splits.length < 4) {
                return;
            }
            int length = splits.length;
            String metricName = splits[length - 1];
            if (!(timeConfig.getLatencyMetrics().contains(metricName)) || !((v
                    .getValue() instanceof Number))) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            String delimiter = "";
            for (int i = 1; i < length - 1; i++) {
                sb.append(delimiter);
                sb.append(splits[i]);
                delimiter = ".";
            }
            OptimizerCacheKey key = new OptimizerCacheKey(time, sb.toString());
            if (optimizerMetricsCache.get(key) == null) {
                optimizerMetricsCache.put(key, OptimizerMetrics.builder().metrics(Maps.newHashMap())
                        .aggregationAlgo(OptimizerMetrics.AggregationAlgo.AVG).build());
            }
            OptimizerMetrics optimizerMetrics = optimizerMetricsCache.get(key);
            if (optimizerMetrics == null) {
                return;
            }
            optimizerMetrics.getMetrics().put(metricName, (Number) v.getValue());

        });
    }
}
