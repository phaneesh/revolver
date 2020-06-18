package io.dropwizard.revolver.core;

import com.codahale.metrics.MetricRegistry;
import com.collections.CollectionUtils;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverConfigHolder;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import io.dropwizard.revolver.http.RevolverHttpContext;
import io.dropwizard.setup.Environment;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

/***
 Created by nitish.goyal on 06/03/20
 ***/
@Slf4j
public class RevolverContextFactory {

    private final Map<RevolverExecutorType, RevolverHttpContext> CONTEXT_MAP = Maps.newHashMap();

    public RevolverContextFactory(Environment environment, RevolverConfigHolder revolverConfigHolder,
            MetricRegistry metrics) {

        Reflections reflections = new Reflections("io.dropwizard.revolver.http");

        Set<Class<? extends RevolverHttpContext>> subTypes = reflections.getSubTypesOf(RevolverHttpContext.class);

        for (Class subType : CollectionUtils.nullSafeSet(subTypes)) {
            try {
                Object obj = subType.newInstance();
                if (obj instanceof RevolverHttpContext) {
                    RevolverHttpContext revolverHttpContext = (RevolverHttpContext) obj;
                    CONTEXT_MAP.putIfAbsent(revolverHttpContext.getExecutorType(),
                            revolverHttpContext);
                    revolverHttpContext.initialize(environment, revolverConfigHolder, metrics);

                }
            } catch (InstantiationException e) {
                log.error("Error occurred while initializing the subtype : {} with exception {}", subType, e);
            } catch (IllegalAccessException e) {
                log.error("Error occurred while initializing the subtype : {} with exception {}", subType, e);
            }
        }
    }

    public RevolverHttpContext getContext(RevolverExecutorType revolverExecutorType) {
        return CONTEXT_MAP.get(revolverExecutorType);
    }

}
