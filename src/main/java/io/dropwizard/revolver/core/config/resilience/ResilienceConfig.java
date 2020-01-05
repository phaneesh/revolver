package io.dropwizard.revolver.core.config.resilience;

import io.dropwizard.revolver.core.config.hystrix.CircuitBreakerConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/***
 Created by nitish.goyal on 23/11/19
 ***/
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ResilienceConfig {

    private CircuitBreakerConfig circuitBreakerConfig;

    private BulkHeadConfig bulkHeadConfig;
}
