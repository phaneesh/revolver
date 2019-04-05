package io.dropwizard.revolver.optimizer.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

/***
 Created by nitish.goyal on 01/04/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OptimizerMetricsCollectorConfig {

    private int repeatAfter = 1;

    private TimeUnit timeUnit = TimeUnit.MINUTES;

    private int cachingWindow = 30;

    private int concurrency = 4;
}
