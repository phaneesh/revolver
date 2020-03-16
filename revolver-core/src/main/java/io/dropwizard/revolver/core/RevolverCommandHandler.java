package io.dropwizard.revolver.core;

import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import java.util.concurrent.CompletableFuture;
import rx.Observable;

/***
 Created by nitish.goyal on 05/03/20
 ***/
public interface RevolverCommandHandler {

    RevolverResponse executeSync(RevolverCommand revolverCommand, RevolverRequest revolverRequest) throws Exception;

    CompletableFuture executeASync(RevolverCommand revolverCommand, RevolverRequest revolverRequest);

    Observable executeAsyncAsObservable(RevolverCommand revolverCommand, RevolverRequest revolverRequest);

    RevolverExecutorType getExecutorType();

}
