package io.dropwizard.revolver.retry;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;

import java.util.concurrent.TimeUnit;

/***
 Created by nitish.goyal on 25/02/19
 ***/
public class RetryUtils {


    private static final Retryer<Boolean> DEFAULT_RETRYER = RetryerBuilder.<Boolean>newBuilder().retryIfException()
            .withStopStrategy(StopStrategies.stopAfterAttempt(RevolverApiRetryConfig.MAX_RETRY))
            .withWaitStrategy(WaitStrategies.exponentialWait(RevolverApiRetryConfig.MAXIMUM_WAIT_TIME_IN_SECONDS, TimeUnit.SECONDS))
            .build();

    private static final long INITIAL_WAIT_IN_MILLS = 200;

    public static Retryer getRetryer(RevolverHttpApiConfig revolverHttpApiConfig) {

        RevolverApiRetryConfig revolverApiRetryConfig = revolverHttpApiConfig.getRetryConfig();
        int maximumTimeInSeconds = revolverHttpApiConfig.getRetryConfig()
                .getMaximumTimeInSeconds();
        if(maximumTimeInSeconds == 0) {
            maximumTimeInSeconds = RevolverApiRetryConfig.MAXIMUM_WAIT_TIME_IN_SECONDS;
        }

        int maxRetry = revolverApiRetryConfig.getMaxRetry();
        if(maxRetry == 0) {
            maxRetry = RevolverApiRetryConfig.MAX_RETRY;
        }

        long incrementByInMillis = revolverApiRetryConfig.getIncrementByInMillis();
        if(incrementByInMillis == 0) {
            incrementByInMillis = RevolverApiRetryConfig.INCREMENT_BY_IN_MILLIS;
        }

        WaitStrategy waitStrategy = revolverApiRetryConfig.getWaitStrategy();
        if(waitStrategy == null) {
            waitStrategy = WaitStrategy.EXPONENTIAL;
        }

        switch (waitStrategy) {
            case FIXED:
                return RetryerBuilder.newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies.fixedWait(maximumTimeInSeconds, TimeUnit.SECONDS))
                        .build();

            case NO_WAIT:
                return RetryerBuilder.newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies.noWait())
                        .build();

            case EXPONENTIAL:
                return RetryerBuilder.newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies.exponentialWait(maximumTimeInSeconds, TimeUnit.SECONDS))
                        .build();

            case INCREMENTAL:
                return RetryerBuilder.newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies.incrementingWait(INITIAL_WAIT_IN_MILLS, TimeUnit.MILLISECONDS, incrementByInMillis,
                                                                          TimeUnit.MILLISECONDS
                                                                         ))
                        .build();

            case FIBONACCI:
                return RetryerBuilder.newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies.fibonacciWait(maximumTimeInSeconds, TimeUnit.SECONDS))
                        .build();
        }
        return DEFAULT_RETRYER;

    }
}
