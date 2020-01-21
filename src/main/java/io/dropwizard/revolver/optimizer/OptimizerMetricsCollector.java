package io.dropwizard.revolver.optimizer;

import static io.dropwizard.revolver.core.model.RevolverExecutorType.HYSTRIX;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.optimizer.config.OptimizerConfig;
import io.dropwizard.revolver.optimizer.hystrix.metrics.LatencyMetric;
import io.dropwizard.revolver.optimizer.hystrix.metrics.OptimizerMetricType;
import io.dropwizard.revolver.optimizer.hystrix.metrics.ThreadPoolMetric;
import io.dropwizard.revolver.optimizer.resilience.metrics.BulkheadMetric;
import java.util.Set;
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

    public static final String ALLOWED_CONCURRENT_CALLS_METRIC_NAME = "resilience4jBulkheadMaxAllowedConcurrentCalls";
    public static final String MAX_ROLLING_ACTIVE_THREADS_METRIC_NAME = "maxRollingActiveThreads";
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
            captureBulkheadConcurrencyMetrics(gauges, time);
            captureLatencyMetrics(gauges, time);
        } catch (Exception e) {
            log.error("Error occurred while executing metrics collector : ", e);
        }
    }

    /**
     * Captures number of available permissions
     *
     * example gauge key name :
     *
     * resilience4jBulkheadAvailableConcurrent_calls.name.{serviceName}.{threadPoolGroupName}
     * resilience4jBulkheadAvailableConcurrent_calls.name.{serviceName}.{apiName}
     */
    private void captureBulkheadConcurrencyMetrics(SortedMap<String, Gauge> gauges, Long time) {
        gauges.forEach((key, gauge) -> {
            updateOptimizerMetricsCache(RevolverExecutorType.RESILIENCE, key, gauge, time, 2,
                    OptimizerMetricType.BULKHEAD, BulkheadMetric.metrics(), gauges);
        });
    }


    /**
     * Captures Rolling max number of active threads during rolling statistical window (rollingMaxActiveThreads)
     *
     * example gauge key names :
     *
     * HystrixThreadPool.{threadpoolName}.rollingMaxActiveThreads, HystrixThreadPool.{serviceName}.{commandName}.rollingMaxActiveThreads
     */
    private void captureThreadPoolMetrics(SortedMap<String, Gauge> gauges, Long time) {
        gauges.forEach((key, gauge) -> {
            updateOptimizerMetricsCache(HYSTRIX, key, gauge, time, 1, OptimizerMetricType.THREAD_POOL,
                    ThreadPoolMetric.metrics(), gauges);
        });
    }

    /**
     * Captures latency metrics for hystrix commands
     *
     * example gauge key names :
     *
     * hystrix : {serviceName}.{serviceName}.{commandName}.latencyExecute_percentile_(995|90|75|50)
     */
    private void captureLatencyMetrics(SortedMap<String, Gauge> gauges, Long time) {
        gauges.forEach((key, gauge) -> {
            updateOptimizerMetricsCache(HYSTRIX, key, gauge, time, 1, OptimizerMetricType.LATENCY,
                    LatencyMetric.metrics(), gauges);
        });

    }

    /**
     * OptimizerMetricsCache: Map<OptimizerCacheKey, OptimizerMetrics>
     *
     * OptimizerCacheKey : {"time":1574682861000, "name": "serviceName.commandName, "metricType":
     * {LATENCY/THREAD_POOL/BULKHEAD}" }
     *
     * OptimizerMetrics : (LATENCY)   { "metrics": { "latencyExecute_percentile_995" : 100,
     * "latencyExecute_percentile_90" : 80, "latencyExecute_percentile_75" : 50, "latencyExecute_percentile_50" : 10 }
     * }
     *
     * (THREAD_POOL) { "metrics": { "propertyValue_maximumSize" : 5, } }
     *
     * (BULKHEAD) { "metrics": { "resilience4jBulkheadAvailableConcurrent_calls" : 5, } }
     */
    private void updateOptimizerMetricsCache(RevolverExecutorType executorType, String key, Gauge gauge, long time,
            int keyStartIndex, OptimizerMetricType metricType, Set<String> metricsToCapture,
            SortedMap<String, Gauge> gauges) {
        String[] splits = key.split("\\.");
        int length = splits.length;
        if (length < metricType.getMinValidLength()) {
            return;
        }

        String metricName = resolveMetricName(executorType, splits);
        if (!(metricsToCapture.contains(metricName))
                || !(gauge.getValue() instanceof Number)) {
            return;
        }

        OptimizerCacheKey cacheKey = getOptimizerCacheKey(executorType, time, splits, keyStartIndex, length,
                metricType);
        if (optimizerMetricsCache.get(cacheKey) == null) {
            optimizerMetricsCache.put(cacheKey, OptimizerMetrics.builder()
                    .metrics(Maps.newHashMap())
                    .build());
        }
        OptimizerMetrics optimizerMetrics = optimizerMetricsCache.get(cacheKey);

        switch (executorType) {
            case RESILIENCE:
                updateMetricsCacheForResilience(optimizerMetrics, splits, gauges, gauge, key);
                break;
            default:
                optimizerMetrics.getMetrics().put(metricName, (Number) gauge.getValue());
                break;
        }
    }

    private void updateMetricsCacheForResilience(OptimizerMetrics optimizerMetrics,
            String[] splits, SortedMap<String, Gauge> gauges, Gauge gauge, String key) {
        String allowedCallsMetricName = getMetricName(splits, ALLOWED_CONCURRENT_CALLS_METRIC_NAME);
        Gauge allowedCallGauge = gauges.get(allowedCallsMetricName);
        if (allowedCallGauge == null || !(allowedCallGauge.getValue() instanceof Number)) {
            return;
        }

        String availableCallsMetricName = getMetricName(splits, "availableCalls");

        Double maxRollingActiveThreads = (Double) allowedCallGauge.getValue() - (Double) gauge.getValue();
        log.debug("Key : {} Value : {}, Available : {}, AvailableCallsMetricName : {}", key, maxRollingActiveThreads,
                gauge.getValue(), availableCallsMetricName);
        try {
            metrics.gauge(availableCallsMetricName, () -> gauge);
        } catch (Exception e) {
            log.error("Error occurred while ingesting metric : {}, Gauge : {}", availableCallsMetricName,
                    gauge.getValue());
        }
        optimizerMetrics.getMetrics().put(MAX_ROLLING_ACTIVE_THREADS_METRIC_NAME, maxRollingActiveThreads);
    }

    private String getMetricName(String[] splits, String prefix) {
        StringBuilder allowedCallsMetricNameBuilder = new StringBuilder();
        allowedCallsMetricNameBuilder.append(prefix);
        for (int i = 1; i < splits.length; i++) {
            allowedCallsMetricNameBuilder.append(".");
            allowedCallsMetricNameBuilder.append(splits[i]);
        }
        return allowedCallsMetricNameBuilder.toString();
    }

    /**
     * Build optimizerCacheKey with current timestamp and name
     *
     * e.g. name format : {serviceName}.{commandName}
     */
    private OptimizerCacheKey getOptimizerCacheKey(RevolverExecutorType executorType,
            Long time, String[] splits, int keyStartIndex, int length,
            OptimizerMetricType metricType) {
        StringBuilder sb = new StringBuilder();
        String delimiter = "";
        int keyEndIndex = findKeyEndIndex(executorType, length);
        for (int i = keyStartIndex; i <= keyEndIndex; i++) {
            sb.append(delimiter);
            sb.append(splits[i]);
            delimiter = ".";
        }

        return new OptimizerCacheKey(time, sb.toString(), metricType);
    }

    private String resolveMetricName(RevolverExecutorType executorType,
            String[] splits) {
        switch (executorType) {
            case HYSTRIX:
                return splits[splits.length - 1];
            case RESILIENCE:
                return splits[0];
            default:
                log.error("Executor type not supported while resolving metric name in optimizer collector");
                throw new IllegalArgumentException(
                        "Executor type not supported while resolving metric name in optimizer collector");
        }
    }

    private int findKeyEndIndex(RevolverExecutorType executorType, int length) {
        switch (executorType) {
            case RESILIENCE:
                return length - 1;
            case HYSTRIX:
                return length - 2;
            default:
                log.error("executor type not supported while resolving key end index in optimizer collector");
                throw new IllegalArgumentException(
                        "executor type not supported while resolving key end index in optimizer collector");
        }
    }
}
