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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.dropwizard.revolver.http.auth.BasicAuthConfig;
import io.dropwizard.revolver.http.auth.TokenAuthConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.NoHttpResponseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.charset.CodingErrorAction;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author phaneesh
 */
@Slf4j
class RevolverHttpClientFactory {

    private static LoadingCache<RevolverHttpServiceConfig, CloseableHttpClient> clientCache = Caffeine.newBuilder()
            .build(RevolverHttpClientFactory::getHttpClient);

    static CloseableHttpClient buildClient(final RevolverHttpServiceConfig serviceConfiguration) {
        Preconditions.checkNotNull(serviceConfiguration);
        return clientCache.get(serviceConfiguration);
    }

    private static CloseableHttpClient getHttpClient(RevolverHttpServiceConfig serviceConfiguration) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, KeyManagementException, UnrecoverableKeyException {
        // Create socket configuration
        SocketConfig socketConfig = SocketConfig.custom()
                .setTcpNoDelay(true)
                .setSoKeepAlive(true)
                .setSoTimeout(0)
                .build();

        // Create connection configuration
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setMalformedInputAction(CodingErrorAction.IGNORE)
                .setUnmappableInputAction(CodingErrorAction.IGNORE)
                .setCharset(Consts.UTF_8)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(serviceConfiguration.getConnectionPoolSize());
        connectionManager.setMaxTotal(serviceConfiguration.getConnectionPoolSize());
        //Fix for: https://issues.apache.org/jira/browse/HTTPCLIENT-1610
        connectionManager.setValidateAfterInactivity(100);
        //Close the connections after keep alive
        connectionManager
                .closeIdleConnections(serviceConfiguration.getConnectionKeepAliveInMillis() <= 0 ? 30000 :
                        serviceConfiguration.getConnectionKeepAliveInMillis(), TimeUnit.MILLISECONDS);
        connectionManager.setDefaultSocketConfig(socketConfig);


        connectionManager.setDefaultConnectionConfig(connectionConfig);


        // Create global request configuration
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setAuthenticationEnabled(serviceConfiguration.isAuthEnabled())
                .setRedirectsEnabled(false)
                .setConnectTimeout(Integer.MAX_VALUE)
                .setConnectionRequestTimeout(Integer.MAX_VALUE)
                .build();


        final HttpClientBuilder client = HttpClients.custom()
                .addInterceptorFirst((HttpRequestInterceptor) (httpRequest, httpContext) -> httpRequest.removeHeaders(HTTP.CONTENT_LEN))
                .setConnectionManager(connectionManager)
                .setRetryHandler((exception, executionCount, context) -> {
                    if(executionCount < 3) {
                        if(exception instanceof NoHttpResponseException) {
                            log.warn("Invalid connection used for service client: {} | Retry count: {}", serviceConfiguration.getService(), executionCount);
                            return true;
                        }
                        if(exception instanceof SocketException) {
                            if(exception.getMessage().contains("Connection reset")) {
                                log.warn("Connection reset error: {} | Retry count: {}", serviceConfiguration.getService(), executionCount);
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .setDefaultRequestConfig(defaultRequestConfig);

        if (serviceConfiguration.isAuthEnabled()) {
            switch (serviceConfiguration.getAuth().getType().toLowerCase()) {
                case "basic":
                    val basicAuthConfig = (BasicAuthConfig) serviceConfiguration.getAuth();
                    if (!Strings.isNullOrEmpty(basicAuthConfig.getUsername())) {
                        throw new RuntimeException(String.format("No valid authentication data for service %s", serviceConfiguration.getAuth().getType()));
                    }
                    BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
                    basicCredentialsProvider.setCredentials(
                            AuthScope.ANY,
                            new UsernamePasswordCredentials(basicAuthConfig.getUsername(),
                                    basicAuthConfig.getPassword()));
                    client.setDefaultCredentialsProvider(basicCredentialsProvider);
                    break;
                case "token":
                    val tokenAuthConfig = (TokenAuthConfig) serviceConfiguration.getAuth();
                    if (Strings.isNullOrEmpty(tokenAuthConfig.getPrefix())) { //No prefix check

                        client.setDefaultHeaders(
                                Collections.singletonList(new BasicHeader(HttpHeaders.AUTHORIZATION,
                                        tokenAuthConfig.getToken())));
                    } else { //with configured prefix
                        client.setDefaultHeaders(
                                Collections.singletonList(new BasicHeader(HttpHeaders.AUTHORIZATION,
                                        String.format("%s %s", tokenAuthConfig.getPrefix(),
                                                tokenAuthConfig.getToken()))));
                    }
                    break;
                default:
                    throw new RuntimeException(String.format("Authentication type %s is not supported", serviceConfiguration.getAuth().getType()));
            }
        }
        if (serviceConfiguration.isSecured()) {
            final String keystorePath = serviceConfiguration.getKeyStorePath();
            final String keystorePassword = (serviceConfiguration.getKeystorePassword() == null) ? "" : serviceConfiguration.getKeystorePassword();
            if (!StringUtils.isBlank(keystorePath)) {
                configureSSL(keystorePath, keystorePassword, client);
            } else {
                client.setSSLHostnameVerifier(new NoopHostnameVerifier());
            }
        }
        return client.build();
    }

    private static void configureSSL(final String keyStorePath, final String keyStorePassword, HttpClientBuilder clientBuilder)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, KeyManagementException, UnrecoverableKeyException {
        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream instream = RevolverHttpClientFactory.class.getClassLoader().getResourceAsStream(keyStorePath)) {
            keyStore.load(instream, keyStorePassword.toCharArray());
        }
        final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
        clientBuilder.setSSLHostnameVerifier(new NoopHostnameVerifier());
        clientBuilder.setSSLContext(sslContext);
    }

}
