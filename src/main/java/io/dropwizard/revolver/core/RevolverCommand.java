/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.dropwizard.revolver.core;

import com.netflix.hystrix.HystrixCommand;
import io.dropwizard.revolver.core.config.ClientConfig;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.RuntimeConfig;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import io.dropwizard.revolver.core.tracing.TraceInfo;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.core.util.RevolverExceptionHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;
import rx.Observable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * @author phaneesh
 */
@Slf4j
public abstract class RevolverCommand<RequestType extends RevolverRequest, ResponseType extends RevolverResponse, ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig, CommandHandlerConfigType extends CommandHandlerConfig> {

    private final ContextType context;
    private final RuntimeConfig runtimeConfig;
    private final ServiceConfigurationType serviceConfiguration;
    private final Map<String, CommandHandlerConfigType> apiConfigurations;
    private ClientConfig clientConfiguration;

    public RevolverCommand(final ContextType context, final ClientConfig clientConfiguration,
                           final RuntimeConfig runtimeConfig, final ServiceConfigurationType serviceConfiguration,
                           final Map<String, CommandHandlerConfigType> apiConfigurations) {
        this.context = context;
        this.clientConfiguration = clientConfiguration;
        this.runtimeConfig = runtimeConfig;
        this.serviceConfiguration = serviceConfiguration;
        this.apiConfigurations = apiConfigurations;
    }

    @SuppressWarnings("unchecked")
    public ResponseType execute(final RequestType request) throws RevolverExecutionException, TimeoutException {
        final CommandHandlerConfigType apiConfiguration = this.apiConfigurations.get(request.getApi());
        if (null == apiConfiguration) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST, "No api spec defined for key: " + request.getApi());
        }
        final RequestType normalizedRequest = RevolverCommandHelper.normalize(request);
        final TraceInfo traceInfo = normalizedRequest.getTrace();
        addContextInfo(request, traceInfo);
        try {
            ResponseType response = (ResponseType) new RevolverCommandHandler(RevolverCommandHelper.setter(this, request.getApi()),
                    this.context, this, normalizedRequest).execute();
            if (log.isDebugEnabled()) {
                log.debug("Command response: " + response);
            }
            return response;
        } catch (Throwable t) {
            Throwable rootCause = ExceptionUtils.getRootCause(t);
            if (rootCause == null) {
                rootCause = t;
            }
            if (rootCause instanceof TimeoutException) {
                throw (TimeoutException) rootCause;
            }
            throw new RevolverExecutionException(RevolverExecutionException.Type.SERVICE_ERROR, rootCause);
        }
    }

    private void addContextInfo(RequestType request, TraceInfo traceInfo) {
        MDC.put("command", RevolverCommandHelper.getName(request));
        MDC.put("transactionId", traceInfo.getTransactionId());
        MDC.put("requestId", traceInfo.getRequestId());
        MDC.put("parentRequestId", traceInfo.getParentRequestId());
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<ResponseType> executeAsync(final RequestType request) {
        final RequestType normalizedRequest = RevolverCommandHelper.normalize(request);
        final TraceInfo traceInfo = normalizedRequest.getTrace();
        addContextInfo(request, traceInfo);
        final Future<ResponseType> responseFuture = new RevolverCommandHandler(RevolverCommandHelper.setter(this, request.getApi()), this.context, this, normalizedRequest).queue();
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        return responseFuture.get();
                    } catch (Throwable t) {
                        throw new RevolverExecutionException(RevolverExecutionException.Type.SERVICE_ERROR, String.format("Error executing command %s", RevolverCommandHelper.getName(request)), RevolverExceptionHelper.getLeafThrowable(t));
                    } finally {
                        removeContextInfo();
                    }
                }
        );
    }

    @SuppressWarnings("unchecked")
    public Observable<ResponseType> executeAsyncAsObservable(final RequestType request) {
        final RequestType normalizedRequest = RevolverCommandHelper.normalize(request);
        final TraceInfo traceInfo = normalizedRequest.getTrace();
        addContextInfo(request, traceInfo);
        return new RevolverCommandHandler(RevolverCommandHelper.setter(this, request.getApi()), this.context, this, normalizedRequest).toObservable();
    }

    private void removeContextInfo() {
        MDC.remove("command");
        MDC.remove("requestId");
        MDC.remove("transactionId");
        MDC.remove("parentRequestId");
    }


    public boolean isFallbackEnabled() {
        return true;
    }

    protected abstract ResponseType execute(final ContextType context, final RequestType request) throws Exception;

    protected abstract ResponseType fallback(final ContextType context, final RequestType request);

    protected ClientConfig getClientConfiguration() {
        return clientConfiguration;
    }

    public RuntimeConfig getRuntimeConfig() {
        return this.runtimeConfig;
    }

    public ServiceConfigurationType getServiceConfiguration() {
        return this.serviceConfiguration;
    }


    public Map<String, CommandHandlerConfigType> getApiConfigurations() {
        return this.apiConfigurations;
    }

    private static class RevolverCommandHandler<RequestType extends RevolverRequest, ResponseType extends RevolverResponse, ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig, CommandHandlerConfigurationType extends CommandHandlerConfig>
            extends HystrixCommand<ResponseType> {
        private final RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> handler;
        private final RequestType request;
        private final ContextType context;

        RevolverCommandHandler(final HystrixCommand.Setter setter, final ContextType context, final RevolverCommand<RequestType, ResponseType, ContextType, ServiceConfigurationType, CommandHandlerConfigurationType> handler, final RequestType request) {
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
    }

}
