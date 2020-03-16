package io.dropwizard.revolver.core;

import static io.dropwizard.revolver.util.ResilienceUtil.getApiName;
import static io.dropwizard.revolver.util.ResilienceUtil.getCbName;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.core.util.RevolverExceptionHelper;
import io.dropwizard.revolver.http.ResilienceHttpContext;
import io.dropwizard.revolver.http.RevolverContext;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.util.ThreadPoolUtil;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;

/***
 Created by nitish.goyal on 05/03/20
 ***/
@Slf4j
public class ResilienceCommandExecutor<RequestType extends RevolverRequest, ResponseType extends RevolverResponse, ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig,
        CommandHandlerConfigurationType extends CommandHandlerConfig> {

    private static final long DEFAULT_TTL = 5000;

    private final RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> revolverCommand;
    private final RequestType revolverRequest;

    public ResilienceCommandExecutor(RevolverCommand revolverCommand, RequestType revolverRequest) {
        this.revolverCommand = revolverCommand;
        this.revolverRequest = revolverRequest;
    }

    public RevolverResponse executeSync() throws Exception {
        try {
            return execute();
        } catch (Exception e) {
            if (e instanceof BulkheadFullException) {
                log.error("BulkheadFullException occurred");
                registerMetric();
            }
            throw e;
        }
    }

    public CompletableFuture<ResponseType> executeASync() {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return execute();
                } catch (Exception e) {
                    throw getException(e);
                }
            });
        } catch (Exception e) {
            throw getException(e);
        }
    }

    public Observable executeAsyncAsObservable() {
        return Observable.fromCallable((() -> {
            try {
                return execute();
            } catch (Exception e) {
                throw getException(e);
            }
        }));
    }

    private ResponseType execute() throws Exception {
        ResilienceHttpContext resilienceHttpContext = getResilienceContext();

        CircuitBreaker circuitBreaker = getCircuitBreaker(resilienceHttpContext);
        Bulkhead bulkhead = getBulkHead(resilienceHttpContext);
        TimeLimiter timeLimiter = getTimeoutConfig(resilienceHttpContext);

        Supplier<Future> supplier = () -> {
            return resilienceHttpContext.getExecutor().submit(() -> {
                return revolverCommand.execute(revolverCommand.getContext(), revolverRequest);
            });
        };

        Callable<ResponseType> timeCallable = TimeLimiter.decorateFutureSupplier(timeLimiter, supplier);
        Callable circuitCallable = CircuitBreaker.decorateCallable(circuitBreaker, timeCallable);
        Callable bulkHeadCallable = Bulkhead.decorateCallable(bulkhead, circuitCallable);
        return (ResponseType) bulkHeadCallable.call();

    }

    private ResilienceHttpContext getResilienceContext() {
        RevolverContext context = revolverCommand.getContext();
        ResilienceHttpContext resilienceHttpContext;
        if (context instanceof ResilienceHttpContext) {
            resilienceHttpContext = (ResilienceHttpContext) context;
        } else {
            resilienceHttpContext = new ResilienceHttpContext();
        }
        return resilienceHttpContext;
    }


    private String getThreadPoolName() {
        ThreadPoolConfig threadPoolConfig = revolverCommand.getApiConfiguration().getRuntime().getThreadPool();
        String threadPoolName = threadPoolConfig.getThreadPoolName();
        if (StringUtils.isEmpty(threadPoolName)) {
            return revolverRequest.getService() + ThreadPoolUtil.DELIMITER + revolverRequest.getApi();
        }
        return ThreadPoolUtil.getThreadPoolNameForService(revolverRequest.getService(), threadPoolName);
    }

    private TimeLimiter getTimeoutConfig(ResilienceHttpContext resilienceHttpContext) {

        ServiceConfigurationType serviceConfiguration = revolverCommand.getServiceConfiguration();
        CommandHandlerConfigurationType apiConfiguration = revolverCommand.getApiConfiguration();
        Map<String, Integer> apiVsTimeout = resilienceHttpContext.getApiVsTimeout();
        long ttl = 0;
        if (apiConfiguration instanceof RevolverHttpApiConfig) {
            String apiName = getApiName(serviceConfiguration, (RevolverHttpApiConfig) apiConfiguration);
            if (apiVsTimeout.get(apiName) != null) {
                ttl = apiVsTimeout.get(apiName);
            }
        }

        if (ttl == 0) {
            //Ideally timeout should be set for all apis. This case should never happen
            if (log.isDebugEnabled()) {
                log.debug("Timeout not set for api : {}", apiConfiguration.getApi());
            }
            ttl = DEFAULT_TTL;
        }
        TimeLimiterConfig config
                = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(ttl)).build();
        return TimeLimiter.of(config);
    }

    private Bulkhead getBulkHead(ResilienceHttpContext resilienceHttpContext) {

        ServiceConfigurationType serviceConfiguration = revolverCommand.getServiceConfiguration();
        Map<String, Bulkhead> bulkheadMap = resilienceHttpContext.getPoolVsBulkHeadMap();
        String threadPoolName = getThreadPoolName();
        Bulkhead bulkhead = bulkheadMap.get(threadPoolName);
        if (bulkhead != null) {
            if (log.isDebugEnabled()) {
                log.debug("BulkheadName : {},  Available Calls : {}, Max Calls : {}, Wait Time : {}",
                        bulkhead.getName(), bulkhead.getMetrics()
                                .getAvailableConcurrentCalls(), bulkhead.getMetrics()
                                .getMaxAllowedConcurrentCalls(), bulkhead.getBulkheadConfig()
                                .getMaxWaitDuration());
            }
            return bulkhead;
        }
        if (log.isDebugEnabled()) {
            log.debug("No bulk head defined for threadPool : {} service : {}, api : {}", threadPoolName,
                    revolverRequest.getService(), revolverRequest.getApi());
        }
        threadPoolName = serviceConfiguration.getService();
        if (StringUtils.isNotEmpty(threadPoolName)) {
            bulkhead = bulkheadMap.get(threadPoolName);
        }

        if (bulkhead == null) {
            //Ideally should never happen
            log.debug("No bulk head defined for service : {}, api : {} threadPool : {}", revolverRequest.getService(),
                    revolverRequest.getApi(), threadPoolName);
            bulkhead = Bulkhead.ofDefaults("revolver");
        }
        return bulkhead;

    }

    private RevolverExecutionException getException(Throwable throwable) {
        return new RevolverExecutionException(RevolverExecutionException.Type.SERVICE_ERROR,
                String.format("Error executing resilience command %s",
                        RevolverCommandHelper.getName(revolverRequest)),
                RevolverExceptionHelper.getLeafThrowable(throwable));
    }

    private CircuitBreaker getCircuitBreaker(ResilienceHttpContext resilienceHttpContext) {
        Map<String, CircuitBreaker> circuitBreakerMap = resilienceHttpContext.getApiVsCircuitBreaker();
        ServiceConfigurationType serviceConfiguration = revolverCommand.getServiceConfiguration();
        CommandHandlerConfigurationType apiConfiguration = revolverCommand.getApiConfiguration();
        String cbName;
        if (apiConfiguration instanceof RevolverHttpApiConfig) {
            cbName = getCbName(serviceConfiguration, (RevolverHttpApiConfig) apiConfiguration);
        } else {
            cbName = serviceConfiguration.getService();
        }

        CircuitBreaker circuitBreaker = circuitBreakerMap.get(cbName);
        if (circuitBreaker != null) {
            return circuitBreaker;
        }

        //Ideally should never happen
        circuitBreaker = resilienceHttpContext.getDefaultCircuitBreaker();
        if (log.isDebugEnabled()) {
            log.debug("DefaultCircuitBreaker : {}", circuitBreaker);
        }
        return circuitBreaker;
    }

    private String getMetricName() {
        return "BulkheadFullException" + "." + getThreadPoolName();
    }

    private void registerMetric() {
        try {
            if (getResilienceContext().getMetrics() == null) {
                return;
            }
            MetricRegistry metrics = getResilienceContext().getMetrics();
            Meter meter = metrics.meter(getMetricName());
            if (meter != null) {
                meter.mark();
            }
        } catch (Exception e) {
            log.error("Error occurred while registering metrics : ", e);
        }
    }
}
