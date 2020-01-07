package io.dropwizard.revolver.optimizer.resilience.metrics;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;


public enum BulkheadMetric {
    BULKHEAD_AVAILABLE_CONCURRENT_CALLS("resilience4jBulkheadAvailableConcurrentCalls");

    @Getter
    private String metricName;

    BulkheadMetric(String metricName) {
        this.metricName = metricName;
    }

    //Reverse map from metricName to ENUM
    private static final Map<String, BulkheadMetric> lookup = new HashMap<>();

    static {
        for (BulkheadMetric s : EnumSet.allOf(BulkheadMetric.class)) {
            lookup.put(s.getMetricName(), s);
        }
    }

    public static BulkheadMetric get(String metricName) {
        return lookup.get(metricName);
    }

    public static Set<String> metrics() {
        return lookup.keySet();
    }
}
