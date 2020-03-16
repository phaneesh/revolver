package io.dropwizard.revolver.core.config.resilience;

import io.dropwizard.revolver.core.config.hystrix.CircuitBreakerConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 23/11/19
 ***/
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ResilienceConfig {

    private BulkHeadConfig bulkHeadConfig;

    private CircuitBreakerConfig circuitBreakerConfig;

    private ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
}
