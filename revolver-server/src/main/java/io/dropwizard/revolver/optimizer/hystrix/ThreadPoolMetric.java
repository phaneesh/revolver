package io.dropwizard.revolver.optimizer.hystrix;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

@Getter
public enum ThreadPoolMetric {
    ROLLING_MAX_ACTIVE_THREADS("rollingMaxActiveThreads"),
    PROPERTY_VALUE_MAXIMUM_SIZE("propertyValue_maximumSize");

    //Reverse map from metricName to ENUM
    private static final Map<String, ThreadPoolMetric> lookup = new HashMap<>();

    static {
        for (ThreadPoolMetric s : EnumSet.allOf(ThreadPoolMetric.class)) {
            lookup.put(s.getMetricName(), s);
        }
    }

    String metricName;

    ThreadPoolMetric(String metricName) {
        this.metricName = metricName;
    }

    public static ThreadPoolMetric get(String metricName) {
        return lookup.get(metricName);
    }

    public static Set<String> metrics() {
        return lookup.keySet();
    }
}
