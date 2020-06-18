package io.dropwizard.revolver.core.config.resilience;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 Created by nitish.goyal on 05/01/20
 ***/
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BulkHeadConfig {

    private int maxWaitTimeInMillis = 100;

}
