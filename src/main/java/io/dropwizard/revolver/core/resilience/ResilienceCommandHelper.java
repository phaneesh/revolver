package io.dropwizard.revolver.core.resilience;

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
        return execute();
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

    private ResponseType execute() throws Exception {
        ResilienceHttpContext resilienceHttpContext = getResilienceContext();

        CircuitBreaker circuitBreaker = getCircuitBreaker(resilienceHttpContext, request,
                handler.getServiceConfiguration(), handler.getApiConfiguration());

        Bulkhead bulkhead = getBulkHead(resilienceHttpContext, request, handler.getServiceConfiguration(),
                handler.getApiConfiguration());

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


    private String getThreadPoolName(RequestType request, CommandHandlerConfigurationType apiConfiguration) {
        ThreadPoolConfig threadPoolConfig = apiConfiguration.getRuntime().getThreadPool();
        String threadPoolName = threadPoolConfig.getThreadPoolName();
        if (StringUtils.isEmpty(threadPoolName)) {
            return request.getService() + "." + request.getApi();
        }
        return request.getService() + "." + threadPoolName;
    }

    private TimeLimiter getTimeoutConfig(ResilienceHttpContext resilienceHttpContext,
            ServiceConfigurationType serviceConfiguration, CommandHandlerConfigurationType apiConfiguration) {

        Map<String, Integer> apiVsTimeout = resilienceHttpContext.getApiVsTimeout();
        long ttl = 0;
        if (apiConfiguration instanceof RevolverHttpApiConfig) {
            String apiName = ResilienceUtil.getApiName(serviceConfiguration, (RevolverHttpApiConfig) apiConfiguration);
            if (apiVsTimeout.get(apiName) != null) {
                ttl = apiVsTimeout.get(apiName);
            }
            log.info("Timeout set for api : {}, time : {}", apiName, ttl);
        }

        if (ttl == 0) {
            //Ideally timeout should be set for all apis. This case should never happen
            log.info("Timeout not set for api : {}", apiConfiguration.getApi());
            ttl = DEFAULT_TTL;
        }
        TimeLimiterConfig config
                = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(ttl)).build();
        return TimeLimiter.of(config);
    }

    private Bulkhead getBulkHead(ResilienceHttpContext resilienceHttpContext, RequestType request,
            ServiceConfigurationType serviceConfiguration, CommandHandlerConfigurationType apiConfiguration) {

        Map<String, Bulkhead> bulkheadMap = resilienceHttpContext.getPoolVsBulkHeadMap();
        String threadPoolName = getThreadPoolName(request, apiConfiguration);
        Bulkhead bulkhead = bulkheadMap.get(threadPoolName);
        if (bulkhead != null) {
            log.info("BulkheadName : {},  Available Calls : {}, Max Calls : {}", bulkhead.getName(),
                    bulkhead.getMetrics().getAvailableConcurrentCalls(),
                    bulkhead.getMetrics().getMaxAllowedConcurrentCalls());
            return bulkhead;
        }
        log.info("No bulk head defined for threadPool : {} service : {}, api : {}", threadPoolName,
                request.getService(),
                request.getApi());
        threadPoolName = serviceConfiguration.getService();
        if (StringUtils.isNotEmpty(threadPoolName)) {
            bulkhead = bulkheadMap.get(threadPoolName);
        }

        if (bulkhead == null) {
            //Ideally should never happen
            log.error("No bulk head defined for service : {}, api : {} threadPool : {}", request.getService(),
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
            cbName = ResilienceUtil.getCbName(serviceConfiguration, (RevolverHttpApiConfig) apiConfiguration);
        } else {
            cbName = serviceConfiguration.getService();
        }

        CircuitBreaker circuitBreaker = circuitBreakerMap.get(cbName);
        if (circuitBreaker != null) {
            return circuitBreaker;
        }

        //Ideally should never happen
        log.error("No circuit breaker defined for service {}, api {}", request.getService(), request.getApi());
        circuitBreaker = resilienceHttpContext.getDefaultCircuitBreaker();
        return circuitBreaker;
    }
}
