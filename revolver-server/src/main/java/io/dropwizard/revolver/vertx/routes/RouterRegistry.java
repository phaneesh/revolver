package io.dropwizard.revolver.vertx.routes;

import io.dropwizard.revolver.resource.RevolverRequestResource;
import io.vertx.ext.web.Router;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data

public class RouterRegistry {

    @Builder
    public RouterRegistry(Router router, RevolverRequestResource revolverRequestResource) {
        RevolverRequestRegistry.builder()
                .router(router)
                .revolverRequestResource(revolverRequestResource)
                .build()
                .register();
    }

}
