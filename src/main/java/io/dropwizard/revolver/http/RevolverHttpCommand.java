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

package io.dropwizard.revolver.http;

import com.collections.CollectionUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.RevolverCommand;
import io.dropwizard.revolver.core.config.ClientConfig;
import io.dropwizard.revolver.core.config.RuntimeConfig;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.discovery.EndpointSpec;
import io.dropwizard.revolver.discovery.model.Endpoint;
import io.dropwizard.revolver.exception.RevolverException;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import io.dropwizard.revolver.http.model.RevolverHttpResponse;
import io.dropwizard.revolver.retry.RetryUtils;
import io.dropwizard.revolver.splitting.*;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author phaneesh
 */
@Slf4j
public class RevolverHttpCommand extends RevolverCommand<RevolverHttpRequest, RevolverHttpResponse, RevolverHttpContext, RevolverHttpServiceConfig, RevolverHttpApiConfig> {


    public static final String CALL_MODE_POLLING = "POLLING";
    public static final String CALL_MODE_CALLBACK = "CALLBACK";
    public static final String CALL_MODE_CALLBACK_SYNC = "CALLBACK_SYNC";

    private final OkHttpClient client;

    @Builder
    public RevolverHttpCommand(final RuntimeConfig runtimeConfig, final ClientConfig clientConfiguration,
                               final RevolverHttpServiceConfig serviceConfiguration,
                               final RevolverHttpApiConfig apiConfiguration) {
        super(new RevolverHttpContext(), clientConfiguration, runtimeConfig, serviceConfiguration, apiConfiguration);
        this.client = RevolverHttpClientFactory.buildClient(serviceConfiguration);
    }

    @Override
    public boolean isFallbackEnabled() {
        return false;
    }

    @Override
    protected RevolverHttpResponse execute(final RevolverHttpContext context, final RevolverHttpRequest request) throws Exception {
        Preconditions.checkNotNull(client);
        final RevolverHttpApiConfig apiConfig = getApiConfiguration();
        if (apiConfig.getMethods().contains(request.getMethod())) {
            switch (request.getMethod()) {
                case GET: {
                    return doGet(request);
                }
                case POST: {
                    return doPost(request);
                }
                case PUT: {
                    return doPut(request);
                }
                case DELETE: {
                    return doDelete(request);
                }
                case HEAD: {
                    return doHead(request);
                }
                case OPTIONS: {
                    return doOptions(request);
                }
                case PATCH: {
                    return doPatch(request);
                }
            }
        }
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("X-REQUEST-PATH", request.getPath());
        headers.putSingle("X-REQUEST-METHOD", request.getMethod().name());
        headers.putSingle("X-REQUEST-API", getApiConfiguration().getApi());
        return RevolverHttpResponse.builder()
                .headers(headers)
                .statusCode(javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode()).build();
    }

    @Override
    protected RevolverHttpResponse fallback(final RevolverHttpContext context, final RevolverHttpRequest requestType) {
        log.error("Fallback triggered for command: " + RevolverCommandHelper.getName(requestType));
        return null;
    }

    private HttpUrl getServiceUrl(final RevolverHttpRequest request, final RevolverHttpApiConfig apiConfiguration) throws RevolverException {
        EndpointSpec endpointSpec = generateEndPoint(apiConfiguration);
        if (endpointSpec == null) {
            endpointSpec = this.getServiceConfiguration().getEndpoint();
        }
        Endpoint endpoint = RevolverBundle.serviceNameResolver.resolve(endpointSpec);
        if (endpoint == null) {
            if (Strings.isNullOrEmpty(getServiceConfiguration().getFallbackAddress())) {
                throw new RevolverException(503, "R999", "Service [" + request.getPath() + "] Unavailable");
            }
            String[] address = getServiceConfiguration().getFallbackAddress().split(":");
            if (address.length == 1) {
                endpoint = Endpoint.builder()
                        .host(address[0])
                        .port(80)
                        .build();
            } else {
                endpoint = Endpoint.builder()
                        .host(address[0])
                        .port(Integer.parseInt(address[1]))
                        .build();
            }
        }
        return generateURI(request, apiConfiguration, endpoint);
    }

    private EndpointSpec generateEndPoint(RevolverHttpApiConfig apiConfiguration) {
        if (null != apiConfiguration.getSplitConfig() && apiConfiguration.getSplitConfig().isEnabled()) {
            return getFromSplitConfig(apiConfiguration);
        } else {
            return this.getServiceConfiguration().getEndpoint();
        }
    }

    private EndpointSpec getFromSplitConfig(RevolverHttpApiConfig apiConfiguration) {
        String serviceEndPoint = getSplitService(apiConfiguration);
        RevolverHttpServiceConfig serviceConfig = this.getServiceConfiguration();
        if (serviceConfig == null || null == serviceConfig.getServiceSplitConfig() ||
                apiConfiguration.getSplitConfig().getSplitStrategy() != SplitStrategy.SERVICE || StringUtils.isEmpty(serviceEndPoint)) {
            return null;
        }
        for (RevolverSplitServiceConfig splitServiceConfig : serviceConfig.getServiceSplitConfig().getConfigs()) {
            if (splitServiceConfig.getName().equals(serviceEndPoint)) {
                return splitServiceConfig.getEndpoint();
            }
        }
        return null;
    }

    private RevolverHttpResponse executeRequest(final RevolverHttpApiConfig apiConfiguration, final Request request,
                                                final boolean readBody, final RevolverHttpRequest originalRequest) throws Exception {
        Response response = null;
        try {
            long start = System.currentTimeMillis();
            if (null != apiConfiguration.getRetryConfig() && apiConfiguration.getRetryConfig().isEnabled()) {
                response = RetryUtils.getRetryer(apiConfiguration)
                        .call(() -> {
                            val url = getServiceUrl(originalRequest, getApiConfiguration());
                            return client.newCall(request.newBuilder()
                                    .url(url).build()).execute();
                        });
            } else {
                response = client.newCall(request).execute();
            }
            long end = System.currentTimeMillis();
            val httpResponse = getHttpResponse(apiConfiguration, response, readBody);
            log.info("[{}/{}] {} {}:{}{} {} {}ms", apiConfiguration.getApi(), apiConfiguration.getPath(),
                    request.method(), request.url().host(), request.url().port(), request.url().encodedPath(),
                    httpResponse.getStatusCode(), (end - start));
            return httpResponse;
        } catch (Exception e) {
            log.error("Error executing service request for service : " + request.url(), e);
            throw e;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private RevolverHttpResponse doGet(final RevolverHttpRequest request) throws Exception {
        Request.Builder httpRequest = initializeRequest(request);
        httpRequest.get();
        return executeRequest(getApiConfiguration(), httpRequest.build(), true, request);
    }

    private Request.Builder initializeRequest(RevolverHttpRequest request) throws RevolverException {
        val url = getServiceUrl(request, getApiConfiguration());
        val httpRequest = new Request.Builder()
                .url(url);
        addHeaders(request, httpRequest);
        trackingHeaders(request, httpRequest);
        return httpRequest;
    }

    private RevolverHttpResponse doOptions(final RevolverHttpRequest request) throws Exception {
        Request.Builder httpRequest = initializeRequest(request);
        httpRequest.method("OPTIONS", null);
        return executeRequest(getApiConfiguration(), httpRequest.build(), true, request);
    }

    private RevolverHttpResponse doHead(final RevolverHttpRequest request) throws Exception {
        Request.Builder httpRequest = initializeRequest(request);
        httpRequest.head();
        return executeRequest(getApiConfiguration(), httpRequest.build(), false, request);
    }

    private RevolverHttpResponse doDelete(final RevolverHttpRequest request) throws Exception {
        Request.Builder httpRequest = initializeRequest(request);
        httpRequest.delete();
        return executeRequest(getApiConfiguration(), httpRequest.build(), true, request);
    }

    private RevolverHttpResponse doPatch(final RevolverHttpRequest request) throws Exception {
        Request.Builder httpRequest = initializeRequest(request);
        if (request.getBody() != null) {
            if (null != request.getHeaders() && StringUtils.isNotBlank(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)))
                httpRequest.patch(RequestBody.create(MediaType.parse(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)), request.getBody()));
            else
                httpRequest.patch(RequestBody.create(MediaType.parse("*/*"), request.getBody()));
        } else {
            httpRequest.patch(RequestBody.create(MediaType.parse("*/*"), new byte[0]));
        }
        return executeRequest(getApiConfiguration(), httpRequest.build(), true, request);
    }

    private RevolverHttpResponse doPost(final RevolverHttpRequest request) throws Exception {
        Request.Builder httpRequest = initializeRequest(request);
        if (request.getBody() != null) {
            if (null != request.getHeaders() && StringUtils.isNotBlank(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)))
                httpRequest.post(RequestBody.create(MediaType.parse(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)), request.getBody()));
            else
                httpRequest.post(RequestBody.create(MediaType.parse("*/*"), request.getBody()));
        } else {
            httpRequest.post(RequestBody.create(MediaType.parse("*/*"), new byte[0]));
        }
        return executeRequest(getApiConfiguration(), httpRequest.build(), true, request);
    }

    private RevolverHttpResponse doPut(final RevolverHttpRequest request) throws Exception {
        Request.Builder httpRequest = initializeRequest(request);
        if (request.getBody() != null) {
            if (null != request.getHeaders() && StringUtils.isNotBlank(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)))
                httpRequest.put(RequestBody.create(MediaType.parse(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)), request.getBody()));
            else
                httpRequest.put(RequestBody.create(MediaType.parse("*/*"), request.getBody()));
        } else {
            httpRequest.put(RequestBody.create(MediaType.parse("*/*"), new byte[0]));
        }
        return executeRequest(getApiConfiguration(), httpRequest.build(), true, request);
    }

    private HttpUrl generateURI(final RevolverHttpRequest request, final RevolverHttpApiConfig apiConfiguration, final Endpoint endpoint) {
        val builder = new HttpUrl.Builder();
        addQueryParams(request, builder);
        if (getServiceConfiguration().isSecured())
            builder.scheme("https");
        else
            builder.scheme("http");
        builder.host(endpoint.getHost()).port(endpoint.getPort()).encodedPath(resolvePath(apiConfiguration, request));
        return builder.build();
    }

    private RevolverHttpResponse getHttpResponse(final RevolverHttpApiConfig apiConfiguration, final Response response, final boolean readBody) throws Exception {
        if (apiConfiguration.getAcceptableResponseCodes() != null && !apiConfiguration.getAcceptableResponseCodes().isEmpty() && !apiConfiguration.getAcceptableResponseCodes().contains(response.code())) {
            if (response.body() != null) {
                log.error("Response: " + response.body().string());
            }
            throw new Exception(String.format("HTTP %s %s failed with [%d - %s]", apiConfiguration.getMethods(), apiConfiguration.getApi(), response.code(), response.message()));
        }
        val headers = new MultivaluedHashMap<String, String>();
        response.headers().names().forEach(h -> headers.putSingle(h, response.header(h)));
        val revolverResponse = RevolverHttpResponse.builder()
                .statusCode(response.code())
                .headers(headers);
        if (readBody && response.body() != null) {
            revolverResponse.body(response.body().bytes());
        }
        return revolverResponse.build();
    }

    private String resolvePath(final RevolverHttpApiConfig httpApiConfiguration, final RevolverHttpRequest request) {
        String uri = null;
        if (Strings.isNullOrEmpty(request.getPath())) {
            if (null != request.getPathParams()) {
                uri = StringSubstitutor.replace(httpApiConfiguration.getPath(), request.getPathParams());
            }
        } else {
            uri = request.getPath();
        }
        if (Strings.isNullOrEmpty(uri)) {
            uri = httpApiConfiguration.getPath();
        }
        return uri.charAt(0) == '/' ? uri : "/" + uri;
    }

    private String getSplitUriFromPathExpression(RevolverHttpApiSplitConfig revolverHttpApiSplitConfig, RevolverHttpRequest request) {

        String path = request.getPath();
        List<PathExpressionSplitConfig>  pathExpressionSplitConfigs = revolverHttpApiSplitConfig.getPathExpressionSplitConfigs();
        if(pathExpressionSplitConfigs.isEmpty()){
            return null;
        }
        for(PathExpressionSplitConfig pathExpressionSplitConfig : CollectionUtils.nullSafeList(pathExpressionSplitConfigs)){
            if(matches(pathExpressionSplitConfig.getExpression(), path)){
                return pathExpressionSplitConfig.getPath();
            }
        }
        return null;
    }

    private String getSplitUriFromHeaderExpression(RevolverHttpApiSplitConfig revolverHttpApiSplitConfig, RevolverHttpRequest request) {

        String path = request.getPath();
        List<PathExpressionSplitConfig>  pathExpressionSplitConfigs = revolverHttpApiSplitConfig.getPathExpressionSplitConfigs();
        if(pathExpressionSplitConfigs.isEmpty()){
            return null;
        }
        for(PathExpressionSplitConfig pathExpressionSplitConfig : pathExpressionSplitConfigs){
            if(matches(pathExpressionSplitConfig.getExpression(), path)){
                return pathExpressionSplitConfig.getPath();
            }
        }
        return null;
    }

    private boolean matches(String expression, String path){
        Pattern pattern = Pattern.compile(expression);
        Matcher matcher = pattern.matcher(path);
        return matcher.matches();
    }

    private String getSplitUri(RevolverHttpApiConfig httpApiConfiguration, RevolverHttpRequest request) {
        double random = Math.random();
        for (SplitConfig splitConfig : httpApiConfiguration.getSplitConfig()
                .getSplits()) {
            if (splitConfig.getFrom() <= random && splitConfig.getTo() > random) {
                return splitConfig.getPath();
            }
        }
        return getUri(httpApiConfiguration, request);
    }

    private String getSplitService(RevolverHttpApiConfig httpApiConfiguration) {
        double random = Math.random();
        for (SplitConfig splitConfig : CollectionUtils.nullSafeList(httpApiConfiguration.getSplitConfig()
                .getSplits())) {
            if (splitConfig.getFrom() <= random && splitConfig.getTo() > random) {
                return splitConfig.getService();
            }
        }
        return StringUtils.EMPTY;
    }

    private String getUri(RevolverHttpApiConfig httpApiConfiguration, RevolverHttpRequest request) {
        String uri = StringUtils.EMPTY;
        if (Strings.isNullOrEmpty(request.getPath())) {
            if (null != request.getPathParams()) {
                uri = StringSubstitutor.replace(httpApiConfiguration.getPath(), request.getPathParams());
            }
        } else {
            uri = request.getPath();
        }
        return uri;
    }

    private void addQueryParams(final RevolverHttpRequest request, final HttpUrl.Builder builder) {
        if (null != request.getQueryParams()) {
            request.getQueryParams().forEach((key, values) -> values.forEach(value -> builder.addQueryParameter(key, value)));
        }
    }

    private void trackingHeaders(final RevolverHttpRequest request, final Request.Builder requestBuilder) {
        if (!getServiceConfiguration().isTrackingHeaders()) {
            return;
        }
        val spanInfo = request.getTrace();
        if (request.getHeaders() == null) {
            request.setHeaders(new MultivaluedHashMap<>());
        }
        List<String> existing = request.getHeaders().keySet().stream().map(String::toLowerCase).collect(Collectors.toList());
        if (!existing.contains(RevolversHttpHeaders.TXN_ID_HEADER.toLowerCase())) {
            requestBuilder.addHeader(RevolversHttpHeaders.TXN_ID_HEADER, spanInfo.getTransactionId());
        }
        if (!existing.contains(RevolversHttpHeaders.REQUEST_ID_HEADER.toLowerCase())) {
            requestBuilder.addHeader(RevolversHttpHeaders.REQUEST_ID_HEADER, spanInfo.getRequestId());
        }
        if (!existing.contains(RevolversHttpHeaders.PARENT_REQUEST_ID_HEADER.toLowerCase())) {
            requestBuilder.addHeader(RevolversHttpHeaders.PARENT_REQUEST_ID_HEADER, spanInfo.getParentRequestId());
        }
        if (!existing.contains(RevolversHttpHeaders.TIMESTAMP_HEADER.toLowerCase())) {
            requestBuilder.addHeader(RevolversHttpHeaders.TIMESTAMP_HEADER, Long.toString(spanInfo.getTimestamp()));
        }
        if (!existing.contains(RevolversHttpHeaders.CLIENT_HEADER.toLowerCase())) {
            requestBuilder.addHeader(RevolversHttpHeaders.CLIENT_HEADER, this.getClientConfiguration().getClientName());
        }
    }

    private void addHeaders(final RevolverHttpRequest request, final Request.Builder requestBuilder) {
        if (null != request.getHeaders()) {
            request.getHeaders().forEach((key, values) -> values.forEach(value -> requestBuilder.addHeader(key, value)));
        }
    }
}
