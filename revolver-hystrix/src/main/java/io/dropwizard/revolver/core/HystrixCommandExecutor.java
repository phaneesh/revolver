package io.dropwizard.revolver.core;

import com.netflix.hystrix.HystrixCommand;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.core.util.RevolverExceptionHelper;
import io.dropwizard.revolver.http.RevolverContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/***
 Created by nitish.goyal on 05/03/20
 ***/
public class HystrixCommandExecutor<RequestType extends RevolverRequest, ResponseType extends RevolverResponse, ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig, CommandHandlerConfigurationType extends CommandHandlerConfig> extends
        HystrixCommand<ResponseType> {

    private final RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> handler;
    private final RequestType request;

    public HystrixCommandExecutor(
            RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> handler,
            RequestType request) {
        super(HystrixCommandHelper.setter(handler, request.getApi()));
        this.handler = handler;
        this.request = request;
    }

    @Override
    protected ResponseType run() throws Exception {
        return this.handler.execute(handler.getContext(), this.request);
    }

    public RevolverResponse executeSync() {
        return execute();
    }

    public CompletableFuture executeASync() {
        Future<ResponseType> responseFuture = this.queue();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return responseFuture.get();
            } catch (Throwable t) {
                throw new RevolverExecutionException(RevolverExecutionException.Type.SERVICE_ERROR,
                        String.format("Error executing command %s",
                                RevolverCommandHelper.getName(request)),
                        RevolverExceptionHelper.getLeafThrowable(t));
            } finally {
                RevolverCommandHelper.removeContextInfo();
            }
        });
    }

    @Override
    protected ResponseType getFallback() {
        return this.handler.fallback(this.handler.getContext(), this.request);
    }
}
