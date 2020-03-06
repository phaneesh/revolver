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

    private int repeatAfter = 30;

    private TimeUnit timeUnit = TimeUnit.SECONDS;

    private int cachingWindowInMinutes = 30;

    private int concurrency = 2;
}
