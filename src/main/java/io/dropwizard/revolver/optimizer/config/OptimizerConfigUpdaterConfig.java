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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizerConfigUpdaterConfig {

    private int repeatAfter = 5;

    private TimeUnit timeUnit = TimeUnit.MINUTES;
}
