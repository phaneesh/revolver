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
import com.google.common.collect.Maps;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
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
import io.dropwizard.revolver.degrade.DegradeRegistry;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author phaneesh
 */
public class RevolverCommandHelper {

  public static String getName(RevolverRequest request) {
    return Joiner.on(".").join(request.getService(), request.getApi());
  }

  public static <T extends RevolverRequest> T normalize(T request) {
    if (null == request) {
      throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST,
          "Request cannot be null");
    }
    TraceInfo traceInfo = request.getTrace();
    if (traceInfo == null) {
      traceInfo = new TraceInfo();
      request.setTrace(traceInfo);
    }
    if (Strings.isNullOrEmpty(traceInfo.getRequestId())) {
      throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST,
          "Request ID must be passed in span");
    }
    if (Strings.isNullOrEmpty(traceInfo.getTransactionId())) {
      throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST,
          "Transaction ID must be passed");
    }
    if (0L == traceInfo.getTimestamp()) {
      traceInfo.setTimestamp(System.currentTimeMillis());
    }
    return request;
  }

  /*
       Group thread pools can be specified at service level. Different api of the service can subscribe to
       different group thread pool
       Timeout would be overridden if provided at individual api level
   */
  public static HystrixCommand.Setter setter(RevolverCommand commandHandler,
                                             String api) {
    RuntimeConfig runtimeConfig = commandHandler.getRuntimeConfig();
    RevolverServiceConfig serviceConfiguration = commandHandler.getServiceConfiguration();
    CommandHandlerConfig config = commandHandler.getApiConfiguration();
    CircuitBreakerConfig circuitBreakerConfig;
    if (null != runtimeConfig) {
      circuitBreakerConfig = runtimeConfig.getCircuitBreaker();
    } else if (null != config.getRuntime() && null != config.getRuntime().getCircuitBreaker()) {
      circuitBreakerConfig = config.getRuntime().getCircuitBreaker();
    } else if (null != serviceConfiguration.getRuntime() && null != serviceConfiguration
        .getRuntime().getCircuitBreaker()) {
      circuitBreakerConfig = serviceConfiguration.getRuntime().getCircuitBreaker();
    } else {
      circuitBreakerConfig = new CircuitBreakerConfig();
    }
    ThreadPoolConfig serviceThreadPoolConfig = null;
    if (null != serviceConfiguration.getRuntime() && null != serviceConfiguration.getRuntime()
        .getThreadPool()) {
      serviceThreadPoolConfig = serviceConfiguration.getRuntime().getThreadPool();
    }
    String keyName = StringUtils.EMPTY;

    MetricsConfig metricsConfig;
    if (null != runtimeConfig) {
      metricsConfig = runtimeConfig.getMetrics();
    } else {
      metricsConfig = new MetricsConfig();
    }

    Map<String, ThreadPoolConfig> threadPoolConfigMap;
    if (null != serviceConfiguration.getThreadPoolGroupConfig() && null != serviceConfiguration
        .getThreadPoolGroupConfig().getThreadPools()) {
      threadPoolConfigMap = serviceConfiguration.getThreadPoolGroupConfig().getThreadPools()
          .stream()
          .collect(Collectors.toMap(ThreadPoolConfig::getThreadPoolName, t -> t));
    } else {
      threadPoolConfigMap = Maps.newHashMap();
    }

    ThreadPoolConfig threadPoolConfig;

    if (null != config.getRuntime() && null != config.getRuntime().getThreadPool()
        && StringUtils.isNotEmpty(config.getRuntime().getThreadPool().getThreadPoolName())
        && null != threadPoolConfigMap
        .get(config.getRuntime().getThreadPool().getThreadPoolName())) {

      threadPoolConfig = threadPoolConfigMap
          .get(config.getRuntime().getThreadPool().getThreadPoolName());
      keyName = threadPoolConfig.getThreadPoolName();

    } else if (config.isSharedPool() && null != serviceThreadPoolConfig) {

      threadPoolConfig = serviceThreadPoolConfig;
      if (StringUtils.isEmpty(keyName)) {
        keyName = Joiner.on(".")
            .join(commandHandler.getServiceConfiguration().getService(), "shared");
      }

    } else if (null != config.getRuntime() && null != config.getRuntime().getThreadPool()) {

      threadPoolConfig = config.getRuntime().getThreadPool();
      keyName = Joiner.on(".")
          .join(commandHandler.getServiceConfiguration().getService(), api);

    } else if (null != serviceThreadPoolConfig) {
      threadPoolConfig = serviceConfiguration.getRuntime().getThreadPool();
      if (StringUtils.isEmpty(keyName)) {
        keyName = Joiner.on(".")
            .join(commandHandler.getServiceConfiguration().getService(), api);
      }

    } else if (null != runtimeConfig) {
      threadPoolConfig = runtimeConfig.getThreadPool();
      keyName = Joiner.on(".")
          .join(commandHandler.getServiceConfiguration().getService(), api);

    } else {
      threadPoolConfig = new ThreadPoolConfig();
      keyName = Joiner.on(".")
          .join(commandHandler.getServiceConfiguration().getService(), api);
    }

    //Setting timeout from api thread pool config
    if (null != config.getRuntime() && null != config.getRuntime().getThreadPool()) {
      threadPoolConfig.setTimeout(config.getRuntime().getThreadPool().getTimeout());
    }

    final String hystrixCommandKey = Joiner.on(".")
        .join(commandHandler.getServiceConfiguration().getService(), api);
    int concurrency = DegradeRegistry.getInstance().getDegradedThreadPool(hystrixCommandKey,
        threadPoolConfig.getConcurrency());
    int timeout = DegradeRegistry.getInstance().getDegradedTimeout(hystrixCommandKey,
        threadPoolConfig.getTimeout());
    int coreSize = (int) Math.ceil(concurrency * metricsConfig.getCorePoolSizeReductionParam());
    return HystrixCommand.Setter.withGroupKey(
        HystrixCommandGroupKey.Factory.asKey(serviceConfiguration.getService()))
        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
            .withExecutionIsolationStrategy(threadPoolConfig.isSemaphoreIsolated()
                ? HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE
                : HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
            .withExecutionIsolationSemaphoreMaxConcurrentRequests(
                threadPoolConfig.getConcurrency())
            .withFallbackIsolationSemaphoreMaxConcurrentRequests(
                threadPoolConfig.getConcurrency())
            .withFallbackEnabled(commandHandler.isFallbackEnabled())
            .withCircuitBreakerErrorThresholdPercentage(
                circuitBreakerConfig.getErrorThresholdPercentage())
            .withCircuitBreakerRequestVolumeThreshold(
                circuitBreakerConfig.getNumAcceptableFailuresInTimeWindow())
            .withCircuitBreakerSleepWindowInMilliseconds(
                circuitBreakerConfig.getWaitTimeBeforeRetry())
            .withExecutionTimeoutInMilliseconds(timeout)
            .withMetricsHealthSnapshotIntervalInMilliseconds(
                metricsConfig.getHealthCheckInterval())
            .withMetricsRollingPercentileBucketSize(
                metricsConfig.getPercentileBucketSize())
            .withMetricsRollingPercentileWindowInMilliseconds(
                metricsConfig.getPercentileTimeInMillis())).andCommandKey(
            HystrixCommandKey.Factory.asKey(hystrixCommandKey))
        .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(keyName))
        .andThreadPoolPropertiesDefaults(
            HystrixThreadPoolProperties.Setter().withCoreSize(coreSize)
                .withMaxQueueSize(threadPoolConfig.getMaxRequestQueueSize())
                .withMaximumSize(concurrency)
                .withKeepAliveTimeMinutes(
                    threadPoolConfig.getKeepAliveTimeInMinutes())
                .withQueueSizeRejectionThreshold(
                    threadPoolConfig.getDynamicRequestQueueSize())
                .withAllowMaximumSizeToDivergeFromCoreSize(true)
                .withMetricsRollingStatisticalWindowBuckets(
                    metricsConfig.getStatsBucketSize())
                .withMetricsRollingStatisticalWindowInMilliseconds(
                    metricsConfig.getStatsTimeInMillis()));
  }
}
