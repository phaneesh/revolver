package io.dropwizard.revolver.optimizer;

import com.google.common.cache.*;
import io.dropwizard.revolver.optimizer.config.OptimizerMetricsCollectorConfig;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.msgpack.jackson.dataformat.Tuple;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Data
@NoArgsConstructor
public class OptimizerMetricsCache {

    private static LinkedHashMap<Tuple<Long, String>, OptimizerMetrics> poolTimeBasedMetricsMap = new LinkedHashMap<>();

    private OptimizerMetricsCollectorConfig optimizerMetricsCollectorConfig;
    private LoadingCache<Tuple<Long, String>, OptimizerMetrics> cache;

    @Builder
    public OptimizerMetricsCache(OptimizerMetricsCollectorConfig optimizerMetricsCollectorConfig) {
        this.optimizerMetricsCollectorConfig = optimizerMetricsCollectorConfig;
        this.cache = CacheBuilder.newBuilder()
                .concurrencyLevel(optimizerMetricsCollectorConfig.getConcurrency())
                .expireAfterWrite(optimizerMetricsCollectorConfig.getCachingWindowInMinutes(), TimeUnit.MINUTES)
                .removalListener(new RemovalListener<Tuple<Long, String>, OptimizerMetrics>() {
                    @Override
                    public void onRemoval(RemovalNotification<Tuple<Long, String>, OptimizerMetrics> notification) {
                        poolTimeBasedMetricsMap.remove(notification.getKey());
                    }
                })
                .build(new CacheLoader<Tuple<Long, String>, OptimizerMetrics>() {
                    @Override
                    public OptimizerMetrics load(@NonNull Tuple<Long, String> key) throws Exception {
                        return poolTimeBasedMetricsMap.get(key);
                    }
                });
    }

    public OptimizerMetrics get(Tuple<Long, String> key) {
        try {
            return cache.get(key);
        } catch (CacheLoader.InvalidCacheLoadException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException("Error while getting value from the cache for the key : " + key);
        }
    }

    public void put(Tuple<Long, String> key, OptimizerMetrics value) {
        cache.put(key, value);
    }

    public Map<Tuple<Long, String>, OptimizerMetrics> getCache() {
        return Collections.unmodifiableMap(cache.asMap());
    }


}
