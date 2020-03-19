package io.dropwizard.revolver.vertx.routes;

import io.dropwizard.revolver.http.config.RevolverHttpApiConfig.RequestMethod;
import io.dropwizard.revolver.resource.RevolverRequestResource;
import io.dropwizard.revolver.vertx.RequestUtil;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/***
 Created by nitish.goyal on 17/03/20
 ***/
@Slf4j
public class RevolverRequestRegistry {

    private final Router router;

    private final RevolverRequestResource revolverRequestResource;

    @Builder
    public RevolverRequestRegistry(Router router, RevolverRequestResource revolverRequestResource) {
        this.router = router;
        this.revolverRequestResource = revolverRequestResource;
    }

    public void register() {
        //Test APIs
        router.route()
                .path("/apis/*")
                .method(HttpMethod.GET)
                .handler(this::getResource);
        router.route()
                .path("/apis/*")
                .method(HttpMethod.POST)
                .handler(BodyHandler.create())
                .handler(this::postResource);
    }

    private void getResource(RoutingContext routingContext) {
        log.info("Executing via vertx for path : {}", routingContext.normalisedPath());
        UriInfo uriInfo = RequestUtil.getUriInfo(routingContext);
        HttpHeaders headers = RequestUtil.getHeaders(routingContext);
        String service = RequestUtil.getServiceName(routingContext);
        String path = RequestUtil.getApiPath(routingContext);
        byte[] body = RequestUtil.getBody(routingContext);
        log.info("Service : {}, Path : {}", service, path);

        try {
            revolverRequestResource.processRequest(routingContext, service, RequestMethod.GET, path, headers, uriInfo,
                    body);
        } catch (Exception e) {
            log.error("Error occurred while executing resource ", e);
        }
        log.info("Execution completed : " + routingContext.getBody());
    }

    private void postResource(RoutingContext routingContext) {
        log.info("Executing via vertx for path : {}", routingContext.normalisedPath());
        UriInfo uriInfo = RequestUtil.getUriInfo(routingContext);
        HttpHeaders headers = RequestUtil.getHeaders(routingContext);
        String service = RequestUtil.getServiceName(routingContext);
        String path = RequestUtil.getApiPath(routingContext);
        byte[] body = RequestUtil.getBody(routingContext);
        String buffer = routingContext.getBody() == null ? "" : routingContext.getBody()
                .toString();
        log.info("Service : {}, Path : {}, Body : {}", service, path, buffer);

        try {
            revolverRequestResource.processRequest(routingContext, service, RequestMethod.POST, path, headers, uriInfo,
                    body);
        } catch (Exception e) {
            log.error("Error occurred while executing resource ", e);
        }
        log.info("Execution completed : " + routingContext.getBody());
    }
}
