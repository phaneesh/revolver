package io.dropwizard.revolver.retry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;

/***
 Created by nitish.goyal on 25/02/19
 ***/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevolverApiRetryConfig {

    public static final int MAX_RETRY = 3;
    public static final int MAXIMUM_WAIT_TIME_IN_SECONDS = 10;
    public static final long INCREMENT_BY_IN_MILLIS = 200;

    @NotNull
    private boolean enabled;

    @DefaultValue("3")
    private int maxRetry = MAX_RETRY;

    @DefaultValue(value = "EXPONENTIAL")
    private WaitStrategy waitStrategy = WaitStrategy.EXPONENTIAL;

    @DefaultValue(value = "10")
    private int maximumTimeInSeconds = MAXIMUM_WAIT_TIME_IN_SECONDS;

    @DefaultValue(value = "200")
    private long incrementByInMillis = INCREMENT_BY_IN_MILLIS;

}
