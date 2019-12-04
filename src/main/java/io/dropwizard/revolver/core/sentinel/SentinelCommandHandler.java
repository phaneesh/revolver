package io.dropwizard.revolver.core.sentinel;

import com.alibaba.csp.sentinel.AsyncEntry;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import io.dropwizard.revolver.core.RevolverCommand;
import io.dropwizard.revolver.core.RevolverContext;
import io.dropwizard.revolver.core.RevolverExecutionException;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.core.util.RevolverExceptionHelper;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;

/***
 Created by nitish.goyal on 18/07/19
 ***/
public class SentinelCommandHandler<RequestType extends RevolverRequest, ResponseType extends RevolverResponse,
        ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig,
        CommandHandlerConfigurationType extends CommandHandlerConfig> {

    private final RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> handler;
    private final RequestType request;
    private final ContextType context;

    public SentinelCommandHandler(ContextType context, RevolverCommand<RequestType, ResponseType, ContextType,
            ServiceConfigurationType, CommandHandlerConfigurationType> handler, RequestType request) {
        this.context = context;
        this.handler = handler;
        this.request = request;
    }


    public ResponseType executeSync() throws Exception {
        String resourceName = getResourceName(handler, request);
        try (Entry entry = SphU.entry(resourceName)) {
            return this.handler.execute(this.context, this.request);
        } catch (Throwable throwable) {
            throw getException(throwable);
        }
    }

    public CompletableFuture<ResponseType> executeASync() {
        try {

            AsyncEntry entry = SphU.asyncEntry(getResourceName(handler, request));
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return handler.execute(context, request);
                } catch (Throwable throwable) {
                    throw getException(throwable);
                } finally {
                    entry.exit();
                }
            });
        } catch (Throwable throwable) {
            throw getException(throwable);
        }
    }

    public Observable executeAsyncAsObservable() {
        return Observable.fromCallable((() -> {
            Entry entry = SphU.entry(getResourceName(handler, request));
            try {
                return handler.execute(context, request);
            } catch (Throwable throwable) {
                throw getException(throwable);
            } finally {
                entry.exit();
            }
        }));
    }

    private String getResourceName(
            RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType,
                    CommandHandlerConfigurationType> handler,
            RequestType request) {
        RevolverServiceConfig serviceConfig = handler.getServiceConfiguration();
        CommandHandlerConfig apiConfig = handler.getApiConfiguration();

        if (apiConfig != null && apiConfig.getSentinelRunTime() != null
                && apiConfig.getSentinelRunTime().getFlowControlConfig() != null && StringUtils
                .isNotEmpty(apiConfig.getSentinelRunTime().getFlowControlConfig().getPoolName())) {
            return apiConfig.getSentinelRunTime().getFlowControlConfig().getPoolName();
        }

        if (serviceConfig != null && serviceConfig.getSentinelCommandConfig() != null
                && serviceConfig.getSentinelCommandConfig().getFlowControlConfig() != null && StringUtils
                .isNotEmpty(serviceConfig.getSentinelCommandConfig().getFlowControlConfig().getPoolName())) {
            return serviceConfig.getSentinelCommandConfig().getFlowControlConfig().getPoolName();
        }
        return request.getApi();
    }


    private RevolverExecutionException getException(Throwable throwable) {
        return new RevolverExecutionException(RevolverExecutionException.Type.SERVICE_ERROR,
                String.format("Error executing command %s",
                        RevolverCommandHelper.getName(request)),
                RevolverExceptionHelper.getLeafThrowable(throwable));
    }
}
