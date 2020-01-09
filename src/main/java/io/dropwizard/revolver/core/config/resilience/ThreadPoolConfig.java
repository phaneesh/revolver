package io.dropwizard.revolver.core.config.resilience;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 09/01/20
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolConfig {

    private int corePoolSize = 50;

    private int maxPoolSize = 1024;

    private int queueSize = 100;

    private int keepAliveTimeInSeconds = 60;
}
