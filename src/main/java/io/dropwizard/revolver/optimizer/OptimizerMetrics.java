package io.dropwizard.revolver.optimizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@AllArgsConstructor
@Data
@Builder
@NoArgsConstructor
public class OptimizerMetrics {

    private Map<String, Number> metrics;

    private AggregationAlgo aggregationAlgo;

    public enum AggregationAlgo {
        AVG,
        MAX
    }
}


