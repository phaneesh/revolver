package io.dropwizard.revolver.splitting;

import io.dropwizard.revolver.discovery.EndpointSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 26/02/19
 ***/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevolverSplitServiceConfig {

    private EndpointSpec endpoint;
    private String name;

}
