package io.dropwizard.revolver.optimizer.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OptimizerConfig {

    private int initialDelay;

    private OptimizerConfigUpdaterConfig configUpdaterConfig;

    private OptimizerMetricsCollectorConfig metricsCollectorConfig;

    private TimeUnit timeUnit;

    private OptimizerConcurrencyConfig concurrencyConfig;

    private boolean enabled;

}
