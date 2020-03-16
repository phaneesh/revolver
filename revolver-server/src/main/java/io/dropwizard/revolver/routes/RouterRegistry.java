package io.dropwizard.revolver.routes;

import static io.dropwizard.revolver.routes.RequestUtil.getApiPath;
import static io.dropwizard.revolver.routes.RequestUtil.getBody;
import static io.dropwizard.revolver.routes.RequestUtil.getHeaders;
import static io.dropwizard.revolver.routes.RequestUtil.getServiceName;
import static io.dropwizard.revolver.routes.RequestUtil.getUriInfo;

import io.dropwizard.revolver.http.config.RevolverHttpApiConfig.RequestMethod;
import io.dropwizard.revolver.resource.RevolverRequestResource;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class RouterRegistry {

    private final Router router;

    private final RevolverRequestResource revolverRequestResource;


    @Builder
    public RouterRegistry(Router router, RevolverRequestResource revolverRequestResource) {
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
                .handler(this::getResource);
    }


    private void getResource(RoutingContext routingContext) {
        UriInfo uriInfo = getUriInfo(routingContext);
        HttpHeaders headers = getHeaders(routingContext);
        String service = getServiceName(routingContext);
        String path = getApiPath(routingContext);
        byte[] body = getBody(routingContext);

        try {
            revolverRequestResource.processRequest(routingContext, service, RequestMethod.GET, path, headers, uriInfo,
                    body);
        } catch (Exception e) {
            log.error("Error occurred while executing resource ", e);
        }
        log.info("Execution completed : " + routingContext.getBody());
    }


}
