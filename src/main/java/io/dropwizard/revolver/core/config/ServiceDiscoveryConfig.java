package io.dropwizard.revolver.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 30/08/19
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDiscoveryConfig {

    @Default
    private boolean watcherDisabled = false;

    @Default
    private int refreshTimeInMs = 1000;

    @Default
    private int waitForDiscoveryInMs = 90000;

}
