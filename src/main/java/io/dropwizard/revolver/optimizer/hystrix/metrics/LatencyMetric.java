package io.dropwizard.revolver.optimizer.hystrix.metrics;


import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

@Getter
public enum LatencyMetric {
    LATENCY_PERCENTILE_995("latencyExecute_percentile_995"),
    LATENCY_PERCENTILE_99("latencyExecute_percentile_99"),
    LATENCY_PERCENTILE_75("latencyExecute_percentile_75"),
    LATENCY_PERCENTILE_50("latencyExecute_percentile_50");

    String metricName;

    LatencyMetric(String metricName) {
        this.metricName = metricName;
    }

    //Reverse map from metricName to ENUM
    private static final Map<String, LatencyMetric> lookup = new HashMap<>();

    static {
        for (LatencyMetric s : EnumSet.allOf(LatencyMetric.class)) {
            lookup.put(s.getMetricName(), s);
        }
    }

    public static LatencyMetric get(String metricName) {
        return lookup.get(metricName);
    }

    public static Set<String> metrics() {
        return lookup.keySet();
    }
}
