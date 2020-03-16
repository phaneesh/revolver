package io.dropwizard.revolver.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 09/04/19
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiLatencyConfig {

    private boolean downgradeDisable;

    private double latency;

}
