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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.RevolverCommand;
import io.dropwizard.revolver.core.config.ClientConfig;
import io.dropwizard.revolver.core.config.RuntimeConfig;
import io.dropwizard.revolver.core.config.ThreadPoolGroupConfig;
import io.dropwizard.revolver.core.util.RevolverCommandHelper;
import io.dropwizard.revolver.discovery.model.Endpoint;
import io.dropwizard.revolver.exception.RevolverException;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import io.dropwizard.revolver.http.model.RevolverHttpResponse;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author phaneesh
 */
@Slf4j
public class RevolverHttpCommand extends RevolverCommand<RevolverHttpRequest, RevolverHttpResponse,
        RevolverHttpContext, RevolverHttpServiceConfig, RevolverHttpApiConfig, ThreadPoolGroupConfig> {


    public static final String CALL_MODE_POLLING = "POLLING";
    public static final String CALL_MODE_CALLBACK = "CALLBACK";
    public static final String CALL_MODE_CALLBACK_SYNC = "CALLBACK_SYNC";

    private final CloseableHttpClient client;

    @Builder
    public RevolverHttpCommand(final RuntimeConfig runtimeConfig, final ClientConfig clientConfiguration,
                               final RevolverHttpServiceConfig serviceConfiguration,
<<<<<<< HEAD
                               final RevolverHttpApiConfig apiConfiguration) {
        super(new RevolverHttpContext(), clientConfiguration, runtimeConfig, serviceConfiguration, apiConfiguration);
=======
                               final Map<String, RevolverHttpApiConfig> apiConfigurations,
                               final RevolverServiceResolver serviceResolver,
                               final ThreadPoolGroupConfig threadPoolGroupConfig) {
        super(new RevolverHttpContext(), clientConfiguration, runtimeConfig, serviceConfiguration, apiConfigurations, threadPoolGroupConfig);
        (this.serviceResolver = serviceResolver).register(serviceConfiguration.getEndpoint());
>>>>>>> Group Thread Pool
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
        if(apiConfig.getMethods().contains(request.getMethod())) {
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

    private URI getServiceUrl(final RevolverHttpRequest request, final RevolverHttpApiConfig apiConfiguration) throws RevolverException {
        Endpoint endpoint = RevolverBundle.serviceNameResolver.resolve((this.getServiceConfiguration()).getEndpoint());
        if(endpoint == null) {
            if(Strings.isNullOrEmpty(getServiceConfiguration().getFallbackAddress())) {
                throw new RevolverException(503, "R999", "Service [" +request.getPath() +"] Unavailable");
            }
            String[] address = getServiceConfiguration().getFallbackAddress().split(":");
            if(address.length == 1) {
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
        try {
            return generateURI(request, apiConfiguration, endpoint);
        } catch (URISyntaxException e) {
            throw new RevolverException(400, "R001", "Bad URI");
        }
    }

    private RevolverHttpResponse executeRequest(final RevolverHttpApiConfig apiConfiguration, final HttpRequestBase request, final boolean readBody) throws Exception {
        CloseableHttpResponse response = null;
        try {
            long start = System.currentTimeMillis();
            response = client.execute(request);
            long end = System.currentTimeMillis();
            val httpResponse = getHttpResponse(apiConfiguration, response, readBody);
            log.info("[{}/{}] {} {}:{}{} {} {}ms", apiConfiguration.getApi(), apiConfiguration.getPath(),
                    request.getMethod(), request.getURI().getHost(), request.getURI().getPort(), request.getURI().getRawPath(),
                    httpResponse.getStatusCode(), (end-start));
            return httpResponse;
        } catch (Exception e) {
            log.error("Error running HTTP GET call: ", e);
            throw e;
        } finally {
            if(response != null) {
                response.close();
            }
        }
    }

    private RevolverHttpResponse doGet(final RevolverHttpRequest request) throws Exception {
        val url = getServiceUrl(request, getApiConfiguration());
        val httpRequest = new HttpGet(url);
        addHeaders(request, httpRequest);
        trackingHeaders(request, httpRequest);
        return executeRequest(getApiConfiguration(), httpRequest, true);
    }

    private RevolverHttpResponse doOptions(final RevolverHttpRequest request) throws Exception {
        val apiConfiguration = getApiConfiguration();
        val url = getServiceUrl(request, apiConfiguration);
        val httpRequest = new HttpOptions(url);
        addHeaders(request, httpRequest);
        trackingHeaders(request, httpRequest);
        return executeRequest(apiConfiguration, httpRequest, true);
    }

    private RevolverHttpResponse doHead(final RevolverHttpRequest request) throws Exception {
        val apiConfiguration = getApiConfiguration();
        val url = getServiceUrl(request, apiConfiguration);
        val httpRequest = new HttpHead(url);
        addHeaders(request, httpRequest);
        trackingHeaders(request, httpRequest);
        return executeRequest(apiConfiguration, httpRequest, false);
    }

    private RevolverHttpResponse doDelete(final RevolverHttpRequest request) throws Exception {
        val apiConfiguration = getApiConfiguration();
        val url = getServiceUrl(request, apiConfiguration);
        val httpRequest = new HttpDelete(url);
        addHeaders(request, httpRequest);
        trackingHeaders(request, httpRequest);
        return executeRequest(apiConfiguration, httpRequest, true);
    }

    private RevolverHttpResponse doPatch(final RevolverHttpRequest request) throws Exception {
        val apiConfiguration = getApiConfiguration();
        val url = getServiceUrl(request, apiConfiguration);
        val httpRequest = new HttpPatch(url);
        addHeaders(request, httpRequest);
        if(request.getBody() != null) {
            if(null != request.getHeaders() && StringUtils.isNotBlank(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)))
                httpRequest.setEntity(new ByteArrayEntity(request.getBody(),
                        ContentType.getByMimeType(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))));
            else
                httpRequest.setEntity(new ByteArrayEntity(request.getBody(), ContentType.WILDCARD));

        } else {
            httpRequest.setEntity(new ByteArrayEntity(new byte[0], ContentType.WILDCARD));
        }
        trackingHeaders(request, httpRequest);
        return executeRequest(apiConfiguration, httpRequest, true);
    }

    private RevolverHttpResponse doPost(final RevolverHttpRequest request) throws Exception {
        val apiConfiguration = getApiConfiguration();
        val url = getServiceUrl(request, apiConfiguration);
        val httpRequest = new HttpPost(url);
        addHeaders(request, httpRequest);
        if(request.getBody() != null) {
            if(null != request.getHeaders() && StringUtils.isNotBlank(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)))
                httpRequest.setEntity(new ByteArrayEntity(request.getBody()));
            else
                httpRequest.setEntity(new ByteArrayEntity(request.getBody()));

        } else {
            httpRequest.setEntity(new ByteArrayEntity(new byte[0]));
        }
        trackingHeaders(request, httpRequest);
        return executeRequest(apiConfiguration, httpRequest, true);
    }

    private RevolverHttpResponse doPut(final RevolverHttpRequest request) throws Exception {
        val apiConfiguration = getApiConfiguration();
        val url = getServiceUrl(request, apiConfiguration);
        val httpRequest = new HttpPut(url);
        addHeaders(request, httpRequest);
        if(request.getBody() != null) {
            if(null != request.getHeaders() && StringUtils.isNotBlank(request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)))
                httpRequest.setEntity(new ByteArrayEntity(request.getBody()));
            else
                httpRequest.setEntity(new ByteArrayEntity(request.getBody()));

        } else {
            httpRequest.setEntity(new ByteArrayEntity(new byte[0]));
        }

        trackingHeaders(request, httpRequest);
        return executeRequest(apiConfiguration, httpRequest, true);
    }

    private URI generateURI(final RevolverHttpRequest request, final RevolverHttpApiConfig apiConfiguration, final Endpoint endpoint) throws URISyntaxException {
        val builder = new URIBuilder();
        addQueryParams(request, builder);
        if (getServiceConfiguration().isSecured()) {
            builder.setScheme("https");
        } else {
            builder.setScheme("http");
        }
        builder.setHost(endpoint.getHost());
        builder.setPort(endpoint.getPort());
        builder.setPath(resolvePath(apiConfiguration, request));
        return builder.build();
    }

    private RevolverHttpResponse getHttpResponse(final RevolverHttpApiConfig apiConfiguration, final HttpResponse response, final boolean readBody) throws Exception {
        if (apiConfiguration.getAcceptableResponseCodes() != null && !apiConfiguration.getAcceptableResponseCodes().isEmpty() && !apiConfiguration.getAcceptableResponseCodes().contains(response.getStatusLine().getStatusCode())) {
            if (response.getEntity() != null) {
                log.error("Response: " + EntityUtils.toString(response.getEntity()));
            }
            throw new Exception(String.format("HTTP %s %s failed with [%d - %s]", apiConfiguration.getMethods(),
                    apiConfiguration.getApi(), response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
        }
        val headers = new MultivaluedHashMap<String, String>();
        Arrays.stream(response.getAllHeaders()).forEach( h -> headers.putSingle( h.getName(), h.getValue()));
        val revolverResponse = RevolverHttpResponse.builder()
                .statusCode(response.getStatusLine().getStatusCode())
                .headers(headers);
        if(readBody && response.getEntity() != null) {
            revolverResponse.body(EntityUtils.toByteArray(response.getEntity()));
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

    private void addQueryParams(final RevolverHttpRequest request, final URIBuilder builder) {
        if (null != request.getQueryParams()) {
            request.getQueryParams().forEach((key, values) -> values.forEach(value ->
                    builder.addParameter(key, value)
            ));
        }
    }

    private void trackingHeaders(final RevolverHttpRequest request, final HttpRequestBase requestBuilder) {
        if (!getServiceConfiguration().isTrackingHeaders()) {
            return;
        }
        val spanInfo = request.getTrace();
        if(request.getHeaders() == null) {
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

    private void addHeaders(final RevolverHttpRequest request, final HttpRequestBase httpRequest) {
        if (null != request.getHeaders()) {
            request.getHeaders().forEach((key, values) -> values.forEach(value -> httpRequest.setHeader(key, value)));
        }
    }

}
