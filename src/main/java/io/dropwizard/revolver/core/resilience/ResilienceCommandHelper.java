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
        log.info("Executing resilience in sync");
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

    private ResilienceHttpContext getResilienceContext() {
        ResilienceHttpContext resilienceHttpContext;
        if (context instanceof ResilienceHttpContext) {
            resilienceHttpContext = (ResilienceHttpContext) context;
        } else {
            resilienceHttpContext = new ResilienceHttpContext();
        }
        return resilienceHttpContext;
    }


    private Bulkhead getBulkHead(ResilienceHttpContext resilienceHttpContext, RequestType request,
            ServiceConfigurationType serviceConfiguration, CommandHandlerConfigurationType apiConfiguration) {

        String threadPoolName = getThreadPoolName(request, apiConfiguration);
        Bulkhead bulkhead = resilienceHttpContext.getPoolVsBulkHeadMap().get(threadPoolName);
        if (bulkhead != null) {
            return bulkhead;
        }
        log.info("No bulk head defined for threadpool {} service {}, api {}", threadPoolName, request.getService(),
                request.getApi());
        threadPoolName = serviceConfiguration.getRuntime().getThreadPool().getThreadPoolName();
        bulkhead = resilienceHttpContext.getPoolVsBulkHeadMap().get(threadPoolName);

        if (bulkhead == null) {
            log.info("No bulk head defined for service {}, api {} threadpoolName", request.getService(),
                    request.getApi(), threadPoolName);
            bulkhead = Bulkhead.ofDefaults("revolver");
        }

        return bulkhead;

    }

    private String getThreadPoolName(RequestType request, CommandHandlerConfigurationType apiConfiguration) {
        ThreadPoolConfig threadPoolConfig = apiConfiguration.getRuntime().getThreadPool();
        String threadPoolName = threadPoolConfig.getThreadPoolName();
        if (StringUtils.isEmpty(threadPoolName)) {
            threadPoolName = request.getService() + "." + request.getApi();
        }
        return request.getService() + "." + threadPoolName;
    }

    private TimeLimiter getTimeoutConfig(
            ResilienceHttpContext resilienceHttpContext,
            ServiceConfigurationType serviceConfiguration, CommandHandlerConfigurationType apiConfiguration) {
        long ttl = 0;
        Map<String, Integer> poolVsTimeout = resilienceHttpContext.getPoolVsTimeout();

        ttl = getTtlFromApiConfig(apiConfiguration, ttl, poolVsTimeout);
        if (ttl == 0) {
            ttl = getTtlFromServiceConfig(serviceConfiguration, poolVsTimeout);
        }
        TimeLimiterConfig config
                = TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(ttl)).build();
        return TimeLimiter.of(config);
    }

    private long getTtlFromServiceConfig(ServiceConfigurationType serviceConfiguration,
            Map<String, Integer> poolVsTimeout) {
        long ttl;
        String threadPoolName =
                serviceConfiguration.getService() + "." + serviceConfiguration.getRuntime().getThreadPool()
                        .getThreadPoolName();
        if (StringUtils.isNotEmpty(threadPoolName) && poolVsTimeout.get(threadPoolName) != 0) {
            ttl = poolVsTimeout
                    .get(threadPoolName);
            log.info("TTL for threadPoolName : {}, ttl : {}", threadPoolName, ttl);
        } else {
            ttl = serviceConfiguration.getRuntime().getThreadPool().getTimeout();
        }
        log.info("TTL from service config for threadPoolName : {}, ttl : {}", threadPoolName, ttl);
        return ttl;
    }

    private long getTtlFromApiConfig(CommandHandlerConfigurationType apiConfiguration,
            long ttl, Map<String, Integer> poolVsTimeout) {
        ThreadPoolConfig threadPoolConfig = apiConfiguration.getRuntime().getThreadPool();
        if (threadPoolConfig != null && threadPoolConfig.getTimeout() != 0) {
            ttl = threadPoolConfig.getTimeout();
            log.info("TTL from api config for api : {}, ttl : {}", apiConfiguration.getApi(), ttl);
        } else if (threadPoolConfig != null
                && poolVsTimeout.get(threadPoolConfig.getThreadPoolName()) != 0) {
            ttl = poolVsTimeout.get(threadPoolConfig.getThreadPoolName());
            log.info("TTL from api config for api : {}, ttl : {}", apiConfiguration.getApi(), ttl);
        }
        return ttl;
    }

    private RevolverExecutionException getException(Throwable throwable) {
        return new RevolverExecutionException(RevolverExecutionException.Type.SERVICE_ERROR,
                String.format("Error executing command %s",
                        RevolverCommandHelper.getName(request)),
                RevolverExceptionHelper.getLeafThrowable(throwable));
    }

    private ResponseType execute() throws Exception {
        ResilienceHttpContext resilienceHttpContext = getResilienceContext();

        CircuitBreaker circuitBreaker = getCircuitBreaker(resilienceHttpContext, request,
                handler.getServiceConfiguration(), handler.getApiConfiguration());

        Bulkhead bulkhead = getBulkHead(resilienceHttpContext, request, handler.getServiceConfiguration(),
                handler.getApiConfiguration());

        TimeLimiter timeLimiter = getTimeoutConfig(resilienceHttpContext, handler.getServiceConfiguration(),
                handler.getApiConfiguration());
        log.info("Time Limiter : " + timeLimiter);

        Supplier<Future> supplier = () -> {
            return resilienceHttpContext.getExecutor().submit(() -> {
                log.info("Executing the resilience request for api :" + request.getApi());
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

    private CircuitBreaker getCircuitBreaker(ResilienceHttpContext resilienceHttpContext, RequestType request,
            ServiceConfigurationType serviceConfiguration,
            CommandHandlerConfigurationType apiConfiguration) {
        String threadPoolName = getThreadPoolName(request, apiConfiguration);
        CircuitBreaker circuitBreaker = resilienceHttpContext.getPoolVsCircuitBreaker().get(threadPoolName);
        if (circuitBreaker != null) {
            return circuitBreaker;
        }
        threadPoolName = serviceConfiguration.getRuntime().getThreadPool().getThreadPoolName();
        circuitBreaker = resilienceHttpContext.getPoolVsCircuitBreaker().get(threadPoolName);

        if (circuitBreaker == null) {
            log.error("No bulk head defined for service {}, api {}", request.getService(), request.getApi());
            circuitBreaker = resilienceHttpContext.getDefaultCircuitBreaker();
        }
        return circuitBreaker;

    }
}
