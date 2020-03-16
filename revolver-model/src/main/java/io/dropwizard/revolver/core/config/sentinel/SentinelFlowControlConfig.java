package io.dropwizard.revolver.core.config.sentinel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 19/07/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SentinelFlowControlConfig {

    private String poolName;

    private double concurrency;

    private SentinelGrade grade;

    private SentinelControlBehavior sentinelControlBehavior;
}
