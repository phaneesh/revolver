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

package io.dropwizard.revolver.callback;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Strings;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.base.core.RevolverRequestState;
import io.dropwizard.revolver.core.config.HystrixCommandConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.discovery.EndpointSpec;
import io.dropwizard.revolver.discovery.model.RangerEndpointSpec;
import io.dropwizard.revolver.discovery.model.SimpleEndpointSpec;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.HeaderUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

/**
 * @author phaneesh
 */
@Data
@Slf4j
public class CallbackHandler {

    private PersistenceProvider persistenceProvider;

    private RevolverConfig revolverConfig;

    private LoadingCache<CallbackConfigKey, RevolverHttpServiceConfig> clientLoadingCache;

    @Builder
    public CallbackHandler(PersistenceProvider persistenceProvider, RevolverConfig revolverConfig) {
        this.persistenceProvider = persistenceProvider;
        this.revolverConfig = revolverConfig;
        this.clientLoadingCache = Caffeine.newBuilder()
                .build(key -> buildConfiguration(key.callbackRequest, key.endpoint));
    }

    @Data
    @Builder
    @EqualsAndHashCode(exclude = "callbackRequest")
    @ToString(exclude = "callbackRequest")
    @AllArgsConstructor
    private static class CallbackConfigKey {
        private String endpoint;
        private RevolverCallbackRequest callbackRequest;
    }

    public void handle(final String requestId, RevolverCallbackResponse response) {
        final RevolverCallbackRequest request = persistenceProvider.request(requestId);
        if (request == null) {
            log.warn("Invalid request: {}", requestId);
            return;
        }
        RevolverRequestState state = persistenceProvider.requestState(requestId);
        if (state == null) {
            log.warn("Invalid request state: {}", requestId);
            return;
        }
        if (Strings.isNullOrEmpty(request.getCallbackUri())) {
            log.warn("Invalid callback uri: {}", requestId);
            return;
        }
        try {
            URI uri = new URI(request.getCallbackUri());
            switch (uri.getScheme()) {
                case "https":
                case "http":
                case "ranger":
                    makeCallback(requestId, uri, request, response);
                    break;
                default:
                    log.warn("Invalid protocol for request: {}", requestId);
            }
            //Save it again for good measure (Overridden because of slow initial api call)
            int mailboxTtl = HeaderUtil.getTTL(request);
            persistenceProvider.saveResponse(requestId, response, mailboxTtl);
        } catch (Exception e) {
            log.error("Invalid callback uri {} for request: {}", request.getCallbackUri(), requestId, e);
        }
    }

    private void makeCallback(final String requestId, final URI uri, final RevolverCallbackRequest callbackRequest,
                              RevolverCallbackResponse callBackResponse) {
        long start = System.currentTimeMillis();
        try {
            String callbackUri = uri.getScheme() + "://" + uri.getHost() + ":" + (uri.getPort() != -1 ? uri.getPort() : "");
            log.info("Callback Request URI: {} | Payload: {}", uri.toString(), new String(callBackResponse.getBody()));
            final RevolverHttpServiceConfig httpCommandConfig = clientLoadingCache.get(CallbackConfigKey.builder()
                    .callbackRequest(callbackRequest)
                    .endpoint(callbackUri)
                    .build());
            if(null == httpCommandConfig) {
                log.error("Invalid callback configuration for key: {} for request: {}", uri.toString(), requestId);
                return;
            }
            final RevolverHttpCommand httpCommand = getCommand(httpCommandConfig);
            final MultivaluedMap<String, String> requestHeaders = new MultivaluedHashMap<>();
            callBackResponse.getHeaders().forEach(requestHeaders::put);
            //Remove host header
            requestHeaders.remove(HttpHeaders.HOST);
            requestHeaders.putSingle(RevolversHttpHeaders.CALLBACK_RESPONSE_CODE, String.valueOf(callBackResponse.getStatusCode()));
            String method = callbackRequest.getHeaders()
                    .getOrDefault(RevolversHttpHeaders.CALLBACK_METHOD_HEADER, Collections.singletonList("POST")).get(0);
            method = Strings.isNullOrEmpty(method) ? "POST" : method;
            final RevolverHttpRequest httpRequest = RevolverHttpRequest.builder()
                    .path(uri.getRawPath())
                    .api("callback")
                    .body(callBackResponse.getBody() == null ? new byte[0] : callBackResponse.getBody())
                    .headers(requestHeaders)
                    .method(RevolverHttpApiConfig.RequestMethod.valueOf(method))
                    .service(httpCommandConfig.getService())
                    .build();
            httpCommand.executeAsyncAsObservable(httpRequest).subscribe((response) -> {
                        if (response.getStatusCode() >= 200 && response.getStatusCode() <= 210) {
                            log.info("Callback success: " + response.toString());
                        } else {
                            log.error("Error from callback for request id: {} | host: {} | Status Code: {} | Response Body: {}",
                                    requestId, uri.getHost(),
                                    response.getStatusCode(), response.getBody() != null ? new String(response.getBody()) : "NONE");
                        }
                    },
                    (error) -> log.error("Error from callback for request id: {} | Error: {}", ExceptionUtils.getRootCauseMessage(error)));
            log.info("Callback complete for request id: {} in {} ms", requestId, (System.currentTimeMillis() - start));
        } catch (Exception e) {
            log.error("Error making callback for: {} for request: {}", uri.toString(), requestId, e);
        }
    }

    private RevolverHttpServiceConfig buildConfiguration(final RevolverCallbackRequest callbackRequest, final String endpoint) throws MalformedURLException, URISyntaxException {
        EndpointSpec endpointSpec = null;
        String apiName = "callback";
        URI uri = new URI(endpoint);
        String serviceName = uri.getHost().replace(".", "-");
        String type = null;
        String method = callbackRequest.getHeaders()
                .getOrDefault(RevolversHttpHeaders.CALLBACK_METHOD_HEADER, Collections.singletonList("POST")).get(0);
        method = Strings.isNullOrEmpty(method) ? "POST" : method;
        String timeout = callbackRequest.getHeaders()
                .getOrDefault(RevolversHttpHeaders.CALLBACK_TIMEOUT_HEADER, Collections.singletonList(String.valueOf(revolverConfig.getCallbackTimeout()))).get(0);
        timeout = Strings.isNullOrEmpty(timeout) ? String.valueOf(revolverConfig.getCallbackTimeout()) : timeout;
        switch (uri.getScheme()) {
            case "https":
            case "http":
                val simpleEndpoint = new SimpleEndpointSpec();
                simpleEndpoint.setHost(uri.getHost());
                simpleEndpoint.setPort((uri.getPort() == 0 || uri.getPort() == -1) ? 80 : uri.getPort());
                endpointSpec = simpleEndpoint;
                type = uri.getScheme();
                break;
            case "ranger": //format for ranger host: environment.service.api
                val rangerEndpoint = new RangerEndpointSpec();
                val discoveryData = uri.getHost().split("\\.");
                if (discoveryData.length != 3) {
                    throw new MalformedURLException("Invalid ranger host format. Accepted format is environment.service.api");
                }
                rangerEndpoint.setEnvironment(discoveryData[0]);
                rangerEndpoint.setService(discoveryData[1]);
                endpointSpec = rangerEndpoint;
                type = "ranger_sharded";
                apiName = discoveryData[2];
        }
        RevolverHttpServiceConfig httpConfig = RevolverHttpServiceConfig.builder()
                .authEnabled(false)
                .connectionPoolSize(10)
                .secured(uri.getScheme().equals("https"))
                .enpoint(endpointSpec)
                .service(serviceName)
                .type(type)
                .api(RevolverHttpApiConfig.configBuilder()
                        .api(apiName)
                        .method(RevolverHttpApiConfig.RequestMethod.valueOf(method))
                        .path(null)
                        .runtime(HystrixCommandConfig.builder()
                                .threadPool(ThreadPoolConfig.builder()
                                        .concurrency(10)
                                        .timeout(Integer.parseInt(timeout))
                                        .build())
                                .build()).build()).build();
        RevolverBundle.addHttpCommand(httpConfig);
        return httpConfig;
    }

    private RevolverHttpCommand getCommand(final RevolverHttpServiceConfig httpConfig) {
        return RevolverBundle.getHttpCommand(httpConfig.getService(), httpConfig.getApis().iterator().next().getApi());
    }
}
