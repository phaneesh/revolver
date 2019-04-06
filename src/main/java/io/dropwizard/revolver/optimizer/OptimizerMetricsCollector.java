package io.dropwizard.revolver.optimizer;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.config.OptimizerTimeoutConfig;
import io.dropwizard.revolver.optimizer.utils.OptimizerUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.Tuple;

import java.util.SortedMap;

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

        SortedMap<String, Gauge> gauges = metrics.getGauges(MetricFilter.startsWith(OptimizerUtils.THREAD_POOL_PREFIX));

        captureThreadPoolMetrics(gauges);

        captureRunTimeMetrics(gauges);
    }


    private void captureThreadPoolMetrics(SortedMap<String, Gauge> gauges) {
        Long time = System.currentTimeMillis();
        gauges.forEach((k, v) -> {
            String[] splits = k.split("\\.");
            if(splits.length < 3) {
                return;
            }
            int length = splits.length;
            String metricName = splits[length - 1];
            if(!(OptimizerUtils.getMetricsToRead()
                    .contains(metricName)) || !((v.getValue() instanceof Number))) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            String delimiter = "";
            for(int i = 1; i < length - 1; i++) {
                sb.append(delimiter);
                sb.append(splits[i]);
                delimiter = ".";
            }
            Tuple<Long, String> key = new Tuple<>(time, sb.toString());
            if(optimizerMetricsCache.get(key) == null) {
                optimizerMetricsCache.put(key, OptimizerMetrics.builder()
                        .metrics(Maps.newHashMap())
                        .build());
            }
            OptimizerMetrics optimizerMetrics = optimizerMetricsCache.get(key);
            if(optimizerMetrics == null) {
                return;
            }
            optimizerMetrics.getMetrics()
                    .put(metricName, (Number)v.getValue());

        });
    }

    private void captureRunTimeMetrics(SortedMap<String, Gauge> gauges) {
        Long time = System.currentTimeMillis();
        OptimizerTimeoutConfig timeoutConfig = optimizerConfig.getTimeoutConfig();
        gauges.forEach((k, v) -> {
            String[] splits = k.split("\\.");
            if(splits.length < 4) {
                return;
            }
            int length = splits.length;
            String metricName = splits[length - 1];
            if(!(timeoutConfig.getTimeoutMetric()
                         .equals(metricName) || !((v.getValue() instanceof Number)))) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            String delimiter = "";
            for(int i = 1; i < length - 1; i++) {
                sb.append(delimiter);
                sb.append(splits[i]);
                delimiter = ".";
            }
            Tuple<Long, String> key = new Tuple<>(time, sb.toString());
            if(optimizerMetricsCache.get(key) == null) {
                optimizerMetricsCache.put(key, OptimizerMetrics.builder()
                        .metrics(Maps.newHashMap())
                        .build());
            }
            OptimizerMetrics optimizerMetrics = optimizerMetricsCache.get(key);
            if(optimizerMetrics == null) {
                return;
            }
            optimizerMetrics.getMetrics()
                    .put(metricName, (Number)v.getValue());

        });
    }
}
