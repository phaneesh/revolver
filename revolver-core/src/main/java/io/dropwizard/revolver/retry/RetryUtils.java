package io.dropwizard.revolver.retry;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

/***
 Created by nitish.goyal on 25/02/19
 ***/
@Slf4j
public class RetryUtils {

    private static final long INITIAL_WAIT_IN_MILLS = 200;

    private static final LoadingCache<RevolverApiRetryConfig, Retryer<Response>> retryerCache = Caffeine
            .newBuilder().build(RetryUtils::build);

    public static Retryer<Response> getRetryer(RevolverHttpApiConfig revolverHttpApiConfig) {
        return retryerCache.get(revolverHttpApiConfig.getRetryConfig());
    }

    public static Retryer<Response> build(RevolverApiRetryConfig revolverApiRetryConfig) {
        int maximumTimeInSeconds = revolverApiRetryConfig.getMaximumTimeInSeconds();
        if (maximumTimeInSeconds == 0) {
            maximumTimeInSeconds = RevolverApiRetryConfig.MAXIMUM_WAIT_TIME_IN_SECONDS;
        }

        int maxRetry = revolverApiRetryConfig.getMaxRetry();
        if (maxRetry == 0) {
            maxRetry = RevolverApiRetryConfig.MAX_RETRY;
        }

        long incrementByInMillis = revolverApiRetryConfig.getIncrementByInMillis();
        if (incrementByInMillis == 0) {
            incrementByInMillis = RevolverApiRetryConfig.INCREMENT_BY_IN_MILLIS;
        }
        WaitStrategy waitStrategy = revolverApiRetryConfig.getWaitStrategy();
        if (waitStrategy == null) {
            waitStrategy = WaitStrategy.EXPONENTIAL;
        }

        switch (waitStrategy) {
            case FIXED:
                return RetryerBuilder.<Response>newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(
                                WaitStrategies.fixedWait(maximumTimeInSeconds, TimeUnit.SECONDS))
                        .build();

            case NO_WAIT:
                return RetryerBuilder.<Response>newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies.noWait()).build();

            case EXPONENTIAL:
                return RetryerBuilder.<Response>newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies
                                .exponentialWait(maximumTimeInSeconds, TimeUnit.SECONDS)).build();

            case INCREMENTAL:
                return RetryerBuilder.<Response>newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies
                                .incrementingWait(INITIAL_WAIT_IN_MILLS, TimeUnit.MILLISECONDS,
                                        incrementByInMillis, TimeUnit.MILLISECONDS)).build();

            case FIBONACCI:
                return RetryerBuilder.<Response>newBuilder()
                        .retryIfResult(new ValidResponseFilter())
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetry))
                        .withWaitStrategy(WaitStrategies
                                .fibonacciWait(maximumTimeInSeconds, TimeUnit.SECONDS)).build();
        }
        return RetryerBuilder.<Response>newBuilder().retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(RevolverApiRetryConfig.MAX_RETRY))
                .withWaitStrategy(WaitStrategies
                        .exponentialWait(RevolverApiRetryConfig.MAXIMUM_WAIT_TIME_IN_SECONDS,
                                TimeUnit.SECONDS)).build();

    }
}
