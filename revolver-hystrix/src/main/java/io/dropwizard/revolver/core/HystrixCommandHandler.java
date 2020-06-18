package io.dropwizard.revolver.core;

import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import io.dropwizard.revolver.http.RevolverContext;
import java.util.concurrent.CompletableFuture;
import rx.Observable;

/***
 Created by nitish.goyal on 18/07/19
 ***/
public class HystrixCommandHandler<RequestType extends RevolverRequest, ResponseType extends RevolverResponse, ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig, CommandHandlerConfigurationType extends CommandHandlerConfig> implements
        RevolverCommandHandler {


    @Override
    public RevolverResponse executeSync(RevolverCommand revolverCommand, RevolverRequest revolverRequest)
            throws Exception {
        return new HystrixCommandExecutor<>(revolverCommand, revolverRequest).executeSync();
    }

    @Override
    public CompletableFuture executeASync(RevolverCommand revolverCommand, RevolverRequest revolverRequest) {
        return new HystrixCommandExecutor<>(revolverCommand, revolverRequest).executeASync();
    }

    @Override
    public Observable executeAsyncAsObservable(RevolverCommand revolverCommand, RevolverRequest revolverRequest) {
        return new HystrixCommandExecutor<>(revolverCommand, revolverRequest).toObservable();
    }

    @Override
    public RevolverExecutorType getExecutorType() {
        return RevolverExecutorType.HYSTRIX;
    }
}
