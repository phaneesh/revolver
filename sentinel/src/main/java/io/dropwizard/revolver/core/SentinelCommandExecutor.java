package io.dropwizard.revolver.core;

import com.alibaba.csp.sentinel.AsyncEntry;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
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
 Created by nitish.goyal on 05/03/20
 ***/
public class SentinelCommandExecutor<RequestType extends RevolverRequest, ResponseType extends RevolverResponse> {

    private final RevolverRequest request;
    private final RevolverCommand revolverCommand;

    public SentinelCommandExecutor(RevolverCommand revolverCommand, RevolverRequest request) {
        this.revolverCommand = revolverCommand;
        this.request = request;
    }

    public ResponseType executeSync() {
        String resourceName = getResourceName();
        try (Entry entry = SphU.entry(resourceName)) {
            return (ResponseType) revolverCommand.execute(revolverCommand.getContext(), this.request);
        } catch (Throwable throwable) {
            throw getException(throwable);
        }
    }

    public CompletableFuture<ResponseType> executeASync() {
        try {
            AsyncEntry entry = SphU.asyncEntry(getResourceName());
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return (ResponseType) revolverCommand.execute(revolverCommand.getContext(), request);
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
            Entry entry = SphU.entry(getResourceName());
            try {
                return revolverCommand.execute(revolverCommand.getContext(), request);
            } catch (Throwable throwable) {
                throw getException(throwable);
            } finally {
                entry.exit();
            }
        }));
    }

    private String getResourceName() {
        RevolverServiceConfig serviceConfig = revolverCommand.getServiceConfiguration();
        CommandHandlerConfig apiConfig = revolverCommand.getApiConfiguration();

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
