package io.dropwizard.revolver.core;

import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;

/***
 Created by nitish.goyal on 22/11/19
 ***/
@Slf4j
public class ResilienceCommandHandler<RequestType extends RevolverRequest, ResponseType extends RevolverResponse> implements
        RevolverCommandHandler {

    @Override
    public RevolverResponse executeSync(RevolverCommand revolverCommand, RevolverRequest revolverRequest)
            throws Exception {
        return new ResilienceCommandExecutor(revolverCommand, revolverRequest).executeSync();
    }

    @Override
    public CompletableFuture<ResponseType> executeASync(RevolverCommand revolverCommand,
            RevolverRequest revolverRequest) {
        return new ResilienceCommandExecutor(revolverCommand, revolverRequest).executeASync();
    }

    @Override
    public Observable executeAsyncAsObservable(RevolverCommand revolverCommand, RevolverRequest revolverRequest) {
        return new ResilienceCommandExecutor(revolverCommand, revolverRequest).executeAsyncAsObservable();
    }

    @Override
    public RevolverExecutorType getExecutorType() {
        return RevolverExecutorType.RESILIENCE;
    }
}
