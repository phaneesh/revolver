package io.dropwizard.revolver.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.ws.rs.DefaultValue;

/***
 Created by nitish.goyal on 09/04/19
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiLatencyConfig {

    @DefaultValue("true")
    private boolean downgradable;

    private double latencyMetricValue;

}
