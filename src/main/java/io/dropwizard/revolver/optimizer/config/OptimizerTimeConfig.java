package io.dropwizard.revolver.optimizer.config;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static io.dropwizard.revolver.optimizer.utils.OptimizerUtils.*;

/***
 Created by nitish.goyal on 05/04/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OptimizerTimeConfig {

    private List<String> latencyMetrics = Lists.newArrayList(LATENCY_PERCENTILE_99, LATENCY_PERCENTILE_50, LATENCY_PERCENTILE_75,
                                                             LATENCY_PERCENTILE_995
                                                            );

    private String timeoutMetric;
    private double getMethodTimeoutBuffer;
    private double allMethodTimeoutBuffer;

    private String appLatencyMetric;
    private String apiLatencyMetric;
    private int appLatencyThresholdValue;


}
