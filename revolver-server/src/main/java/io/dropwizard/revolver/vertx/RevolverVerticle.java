package io.dropwizard.revolver.vertx;

import io.dropwizard.revolver.RevolverBundle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.LogManager;

/***
 Created by nitish.goyal on 16/03/20
 ***/
@Slf4j
public class RevolverVerticle extends AbstractVerticle {


    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        Router router = RevolverBundle.router;

        log.info("Starting server.. Host : {}", System.getenv("HOST"));
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080, Objects.nonNull(System.getenv("HOST")) ? System.getenv("HOST") : "localhost",
                        event -> Runtime.getRuntime()
                                .addShutdownHook(new Thread(() -> {
                                    vertx.createHttpServer()
                                            .requestHandler(router)
                                            .close(r -> log.info("Closing httpServer"));
                                    log.info("Shutting down..");
                                    LogManager.shutdown();
                                })));
    }

}
