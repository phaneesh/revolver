package io.dropwizard.revolver.splitting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/***
 Created by nitish.goyal on 25/02/19
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevolverHttpApiSplitConfig {

    private boolean enabled;

    private List<SplitConfig> splits;

}
