package io.dropwizard.revolver.optimizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OptimalTimeoutAttributes {

    private int optimalTimeout;
    private int meanTimeout;
    private double timeoutBuffer;
}
