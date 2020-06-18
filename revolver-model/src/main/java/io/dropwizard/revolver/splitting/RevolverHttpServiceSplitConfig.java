package io.dropwizard.revolver.splitting;

import java.util.List;
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
public class RevolverHttpServiceSplitConfig {

    private List<RevolverSplitServiceConfig> configs;

}
