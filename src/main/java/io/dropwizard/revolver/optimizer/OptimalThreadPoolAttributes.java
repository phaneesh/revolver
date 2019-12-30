package io.dropwizard.revolver.optimizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OptimalThreadPoolAttributes {
    private int optimalConcurrency;
    private int maxRollingActiveThreads;
}
