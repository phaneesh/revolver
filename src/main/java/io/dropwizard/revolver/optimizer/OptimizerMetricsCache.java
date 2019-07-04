package io.dropwizard.revolver.optimizer;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import io.dropwizard.revolver.optimizer.config.OptimizerMetricsCollectorConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Data
@NoArgsConstructor
public class OptimizerMetricsCache {

    private static LinkedHashMap<OptimizerCacheKey, OptimizerMetrics> poolTimeBasedMetricsMap = new LinkedHashMap<>();

    private OptimizerMetricsCollectorConfig optimizerMetricsCollectorConfig;
    private LoadingCache<OptimizerCacheKey, OptimizerMetrics> cache;

    @Builder
    public OptimizerMetricsCache(OptimizerMetricsCollectorConfig optimizerMetricsCollectorConfig) {
        this.optimizerMetricsCollectorConfig = optimizerMetricsCollectorConfig;
        this.cache = CacheBuilder.newBuilder()
                .concurrencyLevel(optimizerMetricsCollectorConfig.getConcurrency())
                .expireAfterWrite(optimizerMetricsCollectorConfig.getCachingWindowInMinutes(),
                        TimeUnit.MINUTES)
                .removalListener(new RemovalListener<OptimizerCacheKey, OptimizerMetrics>() {
                    @Override
                    public void onRemoval(
                            RemovalNotification<OptimizerCacheKey, OptimizerMetrics> notification) {
                        poolTimeBasedMetricsMap.remove(notification.getKey());
                    }
                }).build(new CacheLoader<OptimizerCacheKey, OptimizerMetrics>() {
                    @Override
                    public OptimizerMetrics load(@NonNull OptimizerCacheKey key) throws Exception {
                        return poolTimeBasedMetricsMap.get(key);
                    }
                });
    }

    public OptimizerMetrics get(OptimizerCacheKey key) {
        try {
            return cache.get(key);
        } catch (CacheLoader.InvalidCacheLoadException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    "Error while getting value from the cache for the key : " + key);
        }
    }

    public void put(OptimizerCacheKey key, OptimizerMetrics value) {
        cache.put(key, value);
    }

    public Map<OptimizerCacheKey, OptimizerMetrics> getCache() {
        return Collections.unmodifiableMap(cache.asMap());
    }


}
