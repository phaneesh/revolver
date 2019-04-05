package io.dropwizard.revolver.optimizer;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
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
public class OptimizerMetricsBuilder implements Runnable {

    private MetricRegistry metrics;
    private OptimizerMetricsCache optimizerMetricsCache;

    @Override
    public void run() {

        Long time = System.currentTimeMillis();

        SortedMap<String, Gauge> gauges = metrics.getGauges(MetricFilter.startsWith(OptimizerUtils.PREFIX));
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
}
