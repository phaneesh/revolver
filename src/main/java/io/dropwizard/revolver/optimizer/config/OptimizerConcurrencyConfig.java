package io.dropwizard.revolver.optimizer.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 30/03/19
 ***/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OptimizerConcurrencyConfig {

    private boolean enabled = true;

    private double maxThreshold = 0.85;

    private double minThreshold = 0.5;

    //Deprecated. Delete it later after config changes
    private double bandwidth = 1.2;

    //Multiply threadPool size at each optimization by threadsMultiplier
    private double threadsMultiplier = 1.2;

    //Increase the thread pool size to maximum : maxPoolExpansionLimit * threadPoolSize
    private double maxPoolExpansionLimit = 1.4;

}
