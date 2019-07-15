package io.dropwizard.revolver.core;

import com.netflix.hystrix.HystrixCommand;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.core.util.RevolverExceptionHelper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/***
 Created by nitish.goyal on 18/07/19
 ***/
public class HystrixCommandHandler<RequestType extends RevolverRequest, ResponseType extends RevolverResponse, ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig, CommandHandlerConfigurationType extends CommandHandlerConfig> extends
        HystrixCommand<ResponseType> {

    private final RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> handler;
    private final RequestType request;
    private final ContextType context;

    public HystrixCommandHandler(HystrixCommand.Setter setter, ContextType context,
            RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> handler,
            RequestType request) {
        super(setter);
        this.context = context;
        this.handler = handler;
        this.request = request;
    }

    @Override
    protected ResponseType run() throws Exception {
        return this.handler.execute(this.context, this.request);
    }

    @Override
    protected ResponseType getFallback() {
        return this.handler.fallback(this.context, this.request);
    }

    public CompletableFuture<ResponseType> executeAsync() {
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


}
