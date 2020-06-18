package io.dropwizard.revolver.core;

import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import java.util.concurrent.CompletableFuture;
import rx.Observable;

/***
 Created by nitish.goyal on 18/07/19
 ***/
public class SentinelCommandHandler<RequestType extends RevolverRequest, ResponseType extends RevolverResponse> implements
        RevolverCommandHandler {


    @Override
    public ResponseType executeSync(RevolverCommand revolverCommand, RevolverRequest revolverRequest) {
        return new SentinelCommandExecutor<RequestType, ResponseType>(revolverCommand, revolverRequest).executeSync();
    }

    @Override
    public CompletableFuture<ResponseType> executeASync(RevolverCommand revolverCommand,
            RevolverRequest revolverRequest) {
        return new SentinelCommandExecutor<RequestType, ResponseType>(revolverCommand, revolverRequest).executeASync();
    }

    @Override
    public Observable executeAsyncAsObservable(RevolverCommand revolverCommand, RevolverRequest revolverRequest) {
        return new SentinelCommandExecutor<RequestType, ResponseType>(revolverCommand, revolverRequest)
                .executeAsyncAsObservable();
    }

    @Override
    public RevolverExecutorType getExecutorType() {
        return RevolverExecutorType.SENTINEL;
    }
}
