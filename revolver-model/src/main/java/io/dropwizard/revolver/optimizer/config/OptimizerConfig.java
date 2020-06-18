package io.dropwizard.revolver.optimizer.config;

import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 29/03/19
 ***/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OptimizerConfig {

    private boolean enabled;

    private int initialDelay;
    private TimeUnit timeUnit;

    private OptimizerConfigUpdaterConfig configUpdaterConfig;
    private OptimizerMetricsCollectorConfig metricsCollectorConfig;
    private OptimizerConcurrencyConfig concurrencyConfig;
    private OptimizerTimeConfig timeConfig;

}
