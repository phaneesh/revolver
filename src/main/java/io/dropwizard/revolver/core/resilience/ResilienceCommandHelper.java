package io.dropwizard.revolver.core.resilience;

import static io.dropwizard.revolver.core.resilience.ResilienceUtil.BULK_HEAD_DELIMITER;
import static io.dropwizard.revolver.core.resilience.ResilienceUtil.DEFAULT_CIRCUIT_BREAKER;
import static io.dropwizard.revolver.core.resilience.ResilienceUtil.circuitBreakerRegistry;
import static io.dropwizard.revolver.core.resilience.ResilienceUtil.getApiName;
import static io.dropwizard.revolver.core.resilience.ResilienceUtil.getCbName;
import static io.dropwizard.revolver.core.resilience.ResilienceUtil.getThreadPoolNameForService;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.revolver.core.RevolverCommand;
import io.dropwizard.revolver.core.RevolverContext;
import io.dropwizard.revolver.core.RevolverExecutionException;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.core.util.RevolverExceptionHelper;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
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
 Created by nitish.goyal on 22/11/19
 ***/
@Slf4j
public class ResilienceCommandHelper<RequestType extends RevolverRequest, ResponseType extends RevolverResponse,
        ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig,
        CommandHandlerConfigurationType extends CommandHandlerConfig> {

    private static final long DEFAULT_TTL = 5000;
    private final RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> handler;
    private final RequestType request;
    private final ContextType context;


    public ResilienceCommandHelper(ContextType context, RevolverCommand<RequestType, ResponseType, ContextType,
            ServiceConfigurationType, CommandHandlerConfigurationType> handler, RequestType request) {
        this.context = context;
        this.handler = handler;
        this.request = request;
    }

    public ResponseType executeSync() throws Exception {
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
        CircuitBreaker circuitBreaker = getCircuitBreaker(resilienceHttpContext, request,
                handler.getServiceConfiguration(), handler.getApiConfiguration());

        Bulkhead bulkhead = getBulkHead(resilienceHttpContext, request, handler.getServiceConfiguration());

        TimeLimiter timeLimiter = getTimeoutConfig(resilienceHttpContext, handler.getServiceConfiguration(),
                handler.getApiConfiguration());

        Supplier<Future> supplier = () -> {
            return resilienceHttpContext.getExecutor().submit(() -> {
                return handler.execute(context, request);
            });
        };

        Callable<ResponseType> timeCallable = TimeLimiter.decorateFutureSupplier(timeLimiter, supplier);
        Callable circuitCallable = CircuitBreaker.decorateCallable(circuitBreaker, timeCallable);
        Callable bulkHeadCallable = Bulkhead.decorateCallable(bulkhead, circuitCallable);
        return (ResponseType) bulkHeadCallable.call();

        /*
        Supplier<RevolverResponse> revolverSupplier = () -> {
            try {
                return handler.execute(context, request);
            } catch (Exception e) {
                throw getException(e);
            }
        };

        Supplier<RevolverResponse> decoratedSupplier = Decorators.ofSupplier(revolverSupplier)
                .withCircuitBreaker(circuitBreaker)
                .withBulkhead(bulkhead)
                .decorate();

        return (ResponseType) Try.ofSupplier(decoratedSupplier).get();*/
    }

    private ResilienceHttpContext getResilienceContext() {
        ResilienceHttpContext resilienceHttpContext;
        if (context instanceof ResilienceHttpContext) {
            resilienceHttpContext = (ResilienceHttpContext) context;
        } else {
            resilienceHttpContext = new ResilienceHttpContext();
        }
        return resilienceHttpContext;
    }


    private String getThreadPoolName() {
        ThreadPoolConfig threadPoolConfig = handler.getApiConfiguration().getRuntime().getThreadPool();
        String threadPoolName = threadPoolConfig.getThreadPoolName();
        if (StringUtils.isEmpty(threadPoolName)) {
            return request.getService() + BULK_HEAD_DELIMITER + request.getApi();
        }
        return getThreadPoolNameForService(request.getService(), threadPoolName);
    }

    private TimeLimiter getTimeoutConfig(ResilienceHttpContext resilienceHttpContext,
            ServiceConfigurationType serviceConfiguration, CommandHandlerConfigurationType apiConfiguration) {

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
            log.debug("Timeout not set for api : {}", apiConfiguration.getApi());
            ttl = DEFAULT_TTL;
        }
        TimeLimiterConfig config
                = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(ttl)).build();
        return TimeLimiter.of(config);
    }

    private Bulkhead getBulkHead(ResilienceHttpContext resilienceHttpContext, RequestType request,
            ServiceConfigurationType serviceConfiguration) {

        Map<String, Bulkhead> bulkheadMap = resilienceHttpContext.getPoolVsBulkHeadMap();
        String threadPoolName = getThreadPoolName();
        Bulkhead bulkhead = bulkheadMap.get(threadPoolName);
        if (bulkhead != null) {
            log.debug("BulkheadName : {},  Available Calls : {}, Max Calls : {}, Wait Time : {}", bulkhead.getName(),
                    bulkhead.getMetrics().getAvailableConcurrentCalls(),
                    bulkhead.getMetrics().getMaxAllowedConcurrentCalls(),
                    bulkhead.getBulkheadConfig().getMaxWaitDuration());
            return bulkhead;
        }
        log.debug("No bulk head defined for threadPool : {} service : {}, api : {}", threadPoolName,
                request.getService(), request.getApi());
        threadPoolName = serviceConfiguration.getService();
        if (StringUtils.isNotEmpty(threadPoolName)) {
            bulkhead = bulkheadMap.get(threadPoolName);
        }

        if (bulkhead == null) {
            //Ideally should never happen
            log.debug("No bulk head defined for service : {}, api : {} threadPool : {}", request.getService(),
                    request.getApi(), threadPoolName);
            bulkhead = Bulkhead.ofDefaults("revolver");
        }
        return bulkhead;

    }

    private RevolverExecutionException getException(Throwable throwable) {
        return new RevolverExecutionException(RevolverExecutionException.Type.SERVICE_ERROR,
                String.format("Error executing resilience command %s",
                        RevolverCommandHelper.getName(request)),
                RevolverExceptionHelper.getLeafThrowable(throwable));
    }

    private CircuitBreaker getCircuitBreaker(ResilienceHttpContext resilienceHttpContext, RequestType request,
            ServiceConfigurationType serviceConfiguration,
            CommandHandlerConfigurationType apiConfiguration) {
        Map<String, CircuitBreaker> circuitBreakerMap = resilienceHttpContext.getApiVsCircuitBreaker();
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
        log.debug("DefaultCircuitBreaker : {}", circuitBreaker);
        if (circuitBreaker == null) {
            circuitBreaker = circuitBreakerRegistry.circuitBreaker(DEFAULT_CIRCUIT_BREAKER);
        }
        return circuitBreaker;
    }

    private String getMetricName() {
        return "BulkheadFullException" + "." + getThreadPoolName();
    }

    private void registerMetric() {

        if (getResilienceContext().getMetrics() == null) {
            return;
        }
        MetricRegistry metrics = getResilienceContext().getMetrics();
        Meter meter = metrics.meter(getMetricName());
        if (meter != null) {
            meter.mark();
        }
    }
}
