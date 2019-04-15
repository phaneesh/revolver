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

    private SplitStrategy splitStrategy;

    private List<SplitConfig> splits;

    private List<PathExpressionSplitConfig> pathExpressionSplitConfigs;

    private List<HeaderExpressionSplitConfig> headerExpressionSplitConfigs;

}
