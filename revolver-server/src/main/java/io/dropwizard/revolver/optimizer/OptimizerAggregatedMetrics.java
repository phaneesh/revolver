package io.dropwizard.revolver.optimizer;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/***
 Created by nitish.goyal on 30/03/19
 ***/
@Data
@Builder
@AllArgsConstructor
public class OptimizerAggregatedMetrics {

    private Map<String, Number> metricsAggValueMap;
}
