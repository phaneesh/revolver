package io.dropwizard.revolver.core;

import com.collections.CollectionUtils;
import com.google.common.collect.Maps;
import io.dropwizard.revolver.core.model.RevolverExecutorType;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

/***
 Created by nitish.goyal on 05/03/20
 ***/
@Slf4j
public class RevolverCommandHandlerFactory {

    private static final Map<RevolverExecutorType, RevolverCommandHandler> FACTORY = Maps.newHashMap();

    public RevolverCommandHandlerFactory() {

        Reflections reflections = new Reflections("io.dropwizard.revolver.core");

        Set<Class<? extends RevolverCommandHandler>> subTypes = reflections.getSubTypesOf(RevolverCommandHandler.class);

        for (Class subType : CollectionUtils.nullSafeSet(subTypes)) {
            try {
                Object obj = subType.newInstance();
                if (obj instanceof RevolverCommandHandler) {
                    RevolverCommandHandler revolverCommandHandler = (RevolverCommandHandler) obj;
                    FACTORY.putIfAbsent(revolverCommandHandler.getExecutorType(),
                            revolverCommandHandler);
                }
            } catch (InstantiationException e) {
                log.error("Error occurred while initializing the subtype : {} with exception {}", subType, e);
            } catch (IllegalAccessException e) {
                log.error("Error occurred while initializing the subtype : {} with exception {}", subType, e);
            }
        }
    }

    public static RevolverCommandHandler getHandler(RevolverExecutorType revolverExecutorType) {
        return FACTORY.get(revolverExecutorType);
    }

}
