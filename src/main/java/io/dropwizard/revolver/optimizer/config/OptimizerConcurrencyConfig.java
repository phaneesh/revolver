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

    private double maxThreshold = 0.85;

    private double minThreshold = 0.6;

    private double bandwidth = 1.2;

}
