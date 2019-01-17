/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.dropwizard.revolver.core.util;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.netflix.hystrix.*;
import io.dropwizard.revolver.core.RevolverCommand;
import io.dropwizard.revolver.core.RevolverExecutionException;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.RuntimeConfig;
import io.dropwizard.revolver.core.config.hystrix.CircuitBreakerConfig;
import io.dropwizard.revolver.core.config.hystrix.MetricsConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.tracing.TraceInfo;

import java.util.Map;

/**
 * @author phaneesh
 */
public class RevolverCommandHelper {

    public static String getName(final RevolverRequest request) {
        return Joiner.on(".").join(request.getService(), request.getApi());
    }

    public static <T extends RevolverRequest> T normalize(final T request) {
        if (null == request) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST, "Request cannot be null");
        }
        TraceInfo traceInfo = request.getTrace();
        if (traceInfo == null) {
            traceInfo = new TraceInfo();
            request.setTrace(traceInfo);
        }
        if (Strings.isNullOrEmpty(traceInfo.getRequestId())) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST, "Request ID must be passed in span");
        }
        if (Strings.isNullOrEmpty(traceInfo.getTransactionId())) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST, "Transaction ID must be passed");
        }
        if (0L == traceInfo.getTimestamp()) {
            traceInfo.setTimestamp(System.currentTimeMillis());
        }
        return request;
    }

    public static HystrixCommand.Setter setter(final RevolverCommand commandHandler, final String api,
                                               Map<String, ThreadPoolConfig> threadPoolConfigMap) {
        final RuntimeConfig runtimeConfig = commandHandler.getRuntimeConfig();
        final RevolverServiceConfig serviceConfiguration = commandHandler.getServiceConfiguration();
        final CommandHandlerConfig config = commandHandler.getApiConfiguration();
        CircuitBreakerConfig circuitBreakerConfig;
        if(null != runtimeConfig) {
            circuitBreakerConfig = runtimeConfig.getCircuitBreaker();
        } else if (null != config.getRuntime() && null != config.getRuntime().getCircuitBreaker()) {
            circuitBreakerConfig = config.getRuntime().getCircuitBreaker();
        } else if (null != serviceConfiguration.getRuntime() && null != serviceConfiguration.getRuntime().getCircuitBreaker()) {
            circuitBreakerConfig = serviceConfiguration.getRuntime().getCircuitBreaker();
        } else {
            circuitBreakerConfig = new CircuitBreakerConfig();
        }
        ThreadPoolConfig threadPoolConfig;
        if(null != config.getRuntime() && null != config.getRuntime().getThreadPool()) {
            threadPoolConfig = config.getRuntime().getThreadPool();
        } else if (null != serviceConfiguration.getRuntime() && null != serviceConfiguration.getRuntime().getThreadPool()) {
            threadPoolConfig = serviceConfiguration.getRuntime().getThreadPool();
        } else if(null != runtimeConfig) {
            threadPoolConfig = runtimeConfig.getThreadPool();
        } else {
            threadPoolConfig = new ThreadPoolConfig();
        }
        MetricsConfig metricsConfig;
        if(null != runtimeConfig) {
            metricsConfig = runtimeConfig.getMetrics();
        } else {
            metricsConfig = new MetricsConfig();
        }

        String keyName;
        //Group thread pool has high precedence over shared thread pool
        if(config.getGroupThreadPool() != null && config.getGroupThreadPool().isEnabled() && threadPoolConfigMap.get
                (config.getGroupThreadPool().getName()) != null){
            threadPoolConfig = threadPoolConfigMap.get(config.getGroupThreadPool().getName());
            keyName = config.getGroupThreadPool().getName();
        }else if(config.isSharedPool()) {
            threadPoolConfig = serviceConfiguration.getRuntime().getThreadPool();
            keyName = Joiner.on(".").join(commandHandler.getServiceConfiguration().getService(), "shared");
        }else {
            keyName = Joiner.on(".").join(commandHandler.getServiceConfiguration().getService(), api);
        }

        return HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory
            .asKey(serviceConfiguration.getService()))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationStrategy(threadPoolConfig.isSemaphoreIsolated() ? HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE : HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
                        .withExecutionIsolationSemaphoreMaxConcurrentRequests(threadPoolConfig.getConcurrency())
                        .withFallbackIsolationSemaphoreMaxConcurrentRequests(threadPoolConfig.getConcurrency())
                        .withFallbackEnabled(commandHandler.isFallbackEnabled())
                        .withCircuitBreakerErrorThresholdPercentage(circuitBreakerConfig.getErrorThresholdPercentage())
                        .withCircuitBreakerRequestVolumeThreshold(circuitBreakerConfig.getNumAcceptableFailuresInTimeWindow())
                        .withCircuitBreakerSleepWindowInMilliseconds(circuitBreakerConfig.getWaitTimeBeforeRetry())
                        .withExecutionTimeoutInMilliseconds(threadPoolConfig.getTimeout())
                        .withMetricsHealthSnapshotIntervalInMilliseconds(metricsConfig.getHealthCheckInterval())
                        .withMetricsRollingPercentileBucketSize(metricsConfig.getPercentileBucketSize())
                        .withMetricsRollingPercentileWindowInMilliseconds(metricsConfig.getPercentileTimeInMillis()))
                .andCommandKey(HystrixCommandKey.Factory.asKey(keyName)).andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(keyName))
                .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter()
                        .withCoreSize(threadPoolConfig.getConcurrency()).withMaxQueueSize(threadPoolConfig.getMaxRequestQueueSize())
                        .withQueueSizeRejectionThreshold(threadPoolConfig.getDynamicRequestQueueSize())
                        .withMetricsRollingStatisticalWindowBuckets(metricsConfig.getStatsBucketSize())
                        .withMetricsRollingStatisticalWindowInMilliseconds(metricsConfig.getStatsTimeInMillis()));
    }
}
