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

import io.dropwizard.revolver.core.config.ClientConfig;
import io.dropwizard.revolver.core.config.CommandHandlerConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
import io.dropwizard.revolver.core.config.RuntimeConfig;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.model.RevolverResponse;
import io.dropwizard.revolver.core.resilience.ResilienceCommandHelper;
import io.dropwizard.revolver.core.sentinel.SentinelCommandHandler;
import io.dropwizard.revolver.core.tracing.TraceInfo;
import io.dropwizard.revolver.core.util.HystrixCommandHelper;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.core.util.RevolverExceptionHelper;
import io.dropwizard.revolver.http.RevolverHttpContext;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import rx.Observable;

/**
 * @author phaneesh
 */
@Slf4j
public abstract class RevolverCommand<RequestType extends RevolverRequest, ResponseType extends RevolverResponse,
        ContextType extends RevolverContext, ServiceConfigurationType extends RevolverServiceConfig,
        CommandHandlerConfigType extends CommandHandlerConfig> {

    private final ContextType context;
    private final RuntimeConfig runtimeConfig;
    private final ServiceConfigurationType serviceConfiguration;
    private final CommandHandlerConfigType apiConfiguration;
    private ClientConfig clientConfiguration;


    public RevolverCommand(ContextType context, ClientConfig clientConfiguration,
            RuntimeConfig runtimeConfig, ServiceConfigurationType serviceConfiguration,
            CommandHandlerConfigType apiConfiguration) {
        if (context == null) {
            context = (ContextType) new RevolverHttpContext();
        }
        this.context = context;
        this.clientConfiguration = clientConfiguration;
        this.runtimeConfig = runtimeConfig;
        this.serviceConfiguration = serviceConfiguration;
        this.apiConfiguration = apiConfiguration;
    }

    @SuppressWarnings("unchecked")
    public ResponseType execute(RequestType request)
            throws RevolverExecutionException, TimeoutException {
        RequestType normalizedRequest = RevolverCommandHelper.normalize(request);
        TraceInfo traceInfo = normalizedRequest.getTrace();
        RevolverCommandHelper.addContextInfo(RevolverCommandHelper.getName(request), traceInfo);
        try {
            ResponseType response;
            RevolverExecutorType revolverExecutorType = getExecutionType(this.getApiConfiguration());
            log.info("Execution type : " + revolverExecutorType);
            switch (revolverExecutorType) {
                case SENTINEL:
                    response = (ResponseType) new SentinelCommandHandler(
                            this.context, this,
                            normalizedRequest).executeSync();
                    break;
                case RESILIENCE:
                    response = new ResilienceCommandHelper<>(
                            this.context, this,
                            normalizedRequest).executeSync();
                    break;
                case HYSTRIX:
                default:
                    response = (ResponseType) new HystrixCommandHandler(
                            HystrixCommandHelper.setter(this, request.getApi()), this.context, this,
                            normalizedRequest).execute();
                    break;
            }
            if (log.isDebugEnabled()) {
                log.debug("Command response: " + response);
            }
            return response;
        } catch (Throwable t) {
            Throwable rootCause = ExceptionUtils.getRootCause(t);
            log.error("Error occurred while executing revolver command for service : " + request
                    .getService() + ", for api : " + request.getApi() + " with error : " + RevolverExceptionHelper
                    .getLeafThrowable(t));
            if (rootCause == null) {
                rootCause = t;
            }
            if (rootCause instanceof TimeoutException) {
                throw (TimeoutException) rootCause;
            }
            throw new RevolverExecutionException(RevolverExecutionException.Type.SERVICE_ERROR,
                    rootCause);
        }
    }

    private RevolverExecutorType getExecutionType(CommandHandlerConfigType apiConfiguration) {
        if (apiConfiguration instanceof RevolverHttpApiConfig) {
            return ((RevolverHttpApiConfig) apiConfiguration).getRevolverExecutorType();
        }
        return RevolverExecutorType.HYSTRIX;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<ResponseType> executeAsync(RequestType request) {
        RequestType normalizedRequest = RevolverCommandHelper.normalize(request);
        TraceInfo traceInfo = normalizedRequest.getTrace();
        RevolverCommandHelper.addContextInfo(RevolverCommandHelper.getName(request), traceInfo);

        RevolverExecutorType revolverExecutorType = request.getRevolverExecutorType();
        switch (revolverExecutorType) {
            case SENTINEL:
                return new SentinelCommandHandler(
                        this.context, this, request).executeASync();
            case RESILIENCE:
                return new ResilienceCommandHelper<>(
                        this.context, this, normalizedRequest).executeASync();
            case HYSTRIX:
            default:
                return new HystrixCommandHandler<>(
                        HystrixCommandHelper.setter(this, request.getApi()), this.context, this,
                        normalizedRequest).executeAsync();

        }
    }

    @SuppressWarnings("unchecked")
    public Observable<ResponseType> executeAsyncAsObservable(RequestType request) {
        RequestType normalizedRequest = RevolverCommandHelper.normalize(request);
        TraceInfo traceInfo = normalizedRequest.getTrace();
        RevolverCommandHelper.addContextInfo(RevolverCommandHelper.getName(request), traceInfo);

        RevolverExecutorType revolverExecutorType = request.getRevolverExecutorType();
        switch (revolverExecutorType) {
            case SENTINEL:
                return new SentinelCommandHandler<>(this.context, this, request).executeAsyncAsObservable();

            case HYSTRIX:
            default:
                return new HystrixCommandHandler<>(HystrixCommandHelper.setter(this, request.getApi()),
                        this.context, this, normalizedRequest).toObservable();
        }

    }


    public boolean isFallbackEnabled() {
        return true;
    }

    public abstract ResponseType execute(ContextType context, RequestType request)
            throws Exception;

    protected abstract ResponseType fallback(ContextType context, RequestType request);

    protected ClientConfig getClientConfiguration() {
        return clientConfiguration;
    }

    public RuntimeConfig getRuntimeConfig() {
        return this.runtimeConfig;
    }

    public ServiceConfigurationType getServiceConfiguration() {
        return this.serviceConfiguration;
    }


    public CommandHandlerConfigType getApiConfiguration() {
        return this.apiConfiguration;
    }


}
