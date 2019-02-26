package io.dropwizard.revolver.splitting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/***
 Created by nitish.goyal on 26/02/19
 ***/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevolverHttpServiceSplitConfig {

    private List<RevolverSplitServiceConfig> configs;

}
