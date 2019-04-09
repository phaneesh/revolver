package io.dropwizard.revolver.optimizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/***
 Created by nitish.goyal on 30/03/19
 ***/
@Data
@Builder
@AllArgsConstructor
public class OptimizerAggregatedMetrics {

    private String pool;

    private Map<String, Number> metricsMaxValueMap;
}
