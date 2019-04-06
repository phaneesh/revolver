package io.dropwizard.revolver.optimizer.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.dropwizard.revolver.optimizer.utils.OptimizerUtils.LATENCY_PERCENTILE_99;

/***
 Created by nitish.goyal on 05/04/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OptimizerTimeoutConfig {

    private String timeoutMetric = LATENCY_PERCENTILE_99;

    private double getMethodTimeoutBuffer = 1.2;

    private double allMethodTimeoutBuffer = 1.3;

    private int defaultBufferForFastApisInMs = 50;
}
