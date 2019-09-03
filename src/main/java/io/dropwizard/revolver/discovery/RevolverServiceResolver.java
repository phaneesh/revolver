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

package io.dropwizard.revolver.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.ranger.healthcheck.HealthcheckStatus;
import com.flipkart.ranger.model.ServiceNode;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import io.appform.dropwizard.discovery.client.ServiceDiscoveryClient;
import io.appform.dropwizard.discovery.common.ShardInfo;
import io.dropwizard.revolver.core.config.ServiceDiscoveryConfig;
import io.dropwizard.revolver.discovery.model.Endpoint;
import io.dropwizard.revolver.discovery.model.RangerEndpointSpec;
import io.dropwizard.revolver.discovery.model.SimpleEndpointSpec;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;

/**
 * @author phaneesh
 */
@Slf4j
public class RevolverServiceResolver {

    private final boolean discoverEnabled;
    private final CuratorFramework curatorFramework;
    private final ServiceResolverConfig resolverConfig;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ServiceDiscoveryConfig serviceDiscoveryConfig;
    private ObjectMapper objectMapper;
    @Getter
    private Map<String, ServiceDiscoveryClient> serviceFinders = Maps.newConcurrentMap();

    @Builder
    public RevolverServiceResolver(ServiceResolverConfig resolverConfig,
            ObjectMapper objectMapper, ServiceDiscoveryConfig serviceDiscoveryConfig) {
        this.resolverConfig = resolverConfig;
        this.objectMapper = objectMapper;
        this.serviceDiscoveryConfig = serviceDiscoveryConfig;
        if (resolverConfig != null) {
            if (!Strings.isNullOrEmpty(resolverConfig.getZkConnectionString())) {
                this.curatorFramework = CuratorFrameworkFactory.builder()
                        .connectString(resolverConfig.getZkConnectionString())
                        .namespace(resolverConfig.getNamespace())
                        .retryPolicy(new RetryNTimes(1000, 500)).build();
                this.curatorFramework.start();
                this.discoverEnabled = true;
            } else {
                this.discoverEnabled = false;
                this.curatorFramework = null;
            }
        } else {
            discoverEnabled = false;
            curatorFramework = null;
        }
    }

    @Builder(builderMethodName = "usingCurator")
    public RevolverServiceResolver(ServiceResolverConfig resolverConfig, ObjectMapper objectMapper,
            CuratorFramework curatorFramework, ServiceDiscoveryConfig serviceDiscoveryConfig) {
        this.resolverConfig = resolverConfig;
        this.objectMapper = objectMapper;
        this.curatorFramework = curatorFramework;
        this.discoverEnabled = true;
        this.serviceDiscoveryConfig = serviceDiscoveryConfig;
    }

    public Endpoint resolve(EndpointSpec endpointSpecification) {
        return new SpecResolver(this.discoverEnabled, this.serviceFinders)
                .resolve(endpointSpecification);
    }


    public void register(EndpointSpec endpointSpecification) {
        endpointSpecification.accept(new SpecVisitor() {

            @Override
            public void visit(SimpleEndpointSpec simpleEndpointSpecification) {
                log.info("Initialized simple service: " + simpleEndpointSpecification.getHost());
            }

            @Override
            @SuppressWarnings("unchecked")
            public void visit(RangerEndpointSpec rangerEndpointSpecification) {
                try {
                    if (!serviceFinders.containsKey(rangerEndpointSpecification
                            .getService())) { //Avoid duplicate registration
                        ServiceDiscoveryClient discoveryClient = ServiceDiscoveryClient
                                .fromCurator().curator(curatorFramework)
                                .environment(rangerEndpointSpecification.getEnvironment())
                                .namespace(resolverConfig.getNamespace()).objectMapper(objectMapper)
                                .serviceName(rangerEndpointSpecification.getService())
                                .disableWatchers(serviceDiscoveryConfig.isWatcherDisabled())
                                .refreshTimeMs(serviceDiscoveryConfig.getRefreshTimeInMs()).build();
                        serviceFinders
                                .put(rangerEndpointSpecification.getService(), discoveryClient);
                        executorService.submit(() -> {
                            try {
                                log.info("Service finder starting for: "
                                        + rangerEndpointSpecification.getService());
                                discoveryClient.start();
                                log.info("Initialized ZK service: " + rangerEndpointSpecification
                                        .getService());
                            } catch (Exception e) {
                                log.error("Error registering service finder started for: "
                                        + rangerEndpointSpecification.getService(), e);
                            }
                            return null;
                        });
                    }
                } catch (Exception e) {
                    log.error("Error registering handler for service: " + rangerEndpointSpecification
                            .getService(), e);
                }
            }
        });
    }

    private static class SpecResolver implements SpecVisitor {

        private final boolean discoverEnabled;
        private final Map<String, ServiceDiscoveryClient> serviceDiscoveryClients;
        private Endpoint endpoint;

        private SpecResolver(boolean discoverEnabled,
                Map<String, ServiceDiscoveryClient> serviceDiscoveryClients) {
            this.discoverEnabled = discoverEnabled;
            this.serviceDiscoveryClients = serviceDiscoveryClients;
        }

        @Override
        public void visit(SimpleEndpointSpec simpleEndpointSpecification) {
            this.endpoint = Endpoint.builder().host(simpleEndpointSpecification.getHost())
                    .port(simpleEndpointSpecification.getPort()).build();
        }

        @Override
        public void visit(RangerEndpointSpec rangerEndpointSpecification) {
            if (!this.discoverEnabled) {
                throw new IllegalAccessError(
                        "Zookeeper is not initialized in config. Discovery based lookups will not be possible.");
            }
            Optional<ServiceNode<ShardInfo>> node = serviceDiscoveryClients
                    .get(rangerEndpointSpecification.getService()).getNode();
            //Get only the nodes that are healthy
            node.ifPresent(n -> {
                if (n.getHealthcheckStatus() == HealthcheckStatus.healthy) {
                    this.endpoint = Endpoint.builder().host(n.getHost()).port(n.getPort()).build();
                }
            });
        }

        Endpoint resolve(EndpointSpec specification) {
            specification.accept(this);
            return this.endpoint;
        }
    }
}
