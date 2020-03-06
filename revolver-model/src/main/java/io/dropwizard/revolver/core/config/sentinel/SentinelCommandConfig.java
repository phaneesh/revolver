package io.dropwizard.revolver.core.config.sentinel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 19/07/19
 ***/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentinelCommandConfig {

    private SentinelFlowControlConfig flowControlConfig;

}
