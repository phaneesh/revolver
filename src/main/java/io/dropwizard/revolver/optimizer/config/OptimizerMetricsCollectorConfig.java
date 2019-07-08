package io.dropwizard.revolver.optimizer.config;

import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 01/04/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OptimizerMetricsCollectorConfig {

    private int repeatAfter = 2;

    private TimeUnit timeUnit = TimeUnit.MINUTES;

    private int cachingWindowInMinutes = 30;

    private int concurrency = 2;
}
