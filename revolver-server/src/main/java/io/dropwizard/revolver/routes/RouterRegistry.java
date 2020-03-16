package io.dropwizard.revolver.routes;

import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig.RequestMethod;
import io.dropwizard.revolver.resource.RevolverRequestResource;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.TimeoutHandler;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.MatchResult;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.internal.process.Endpoint;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.RuntimeResource;
import org.glassfish.jersey.uri.UriTemplate;

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
        router.route("/apis/*")
                .method(HttpMethod.GET)
                .handler(this::testResource)
                .handler(TimeoutHandler.create(1000, 504));
        router.route()
                .path("/apis/*")
                .method(HttpMethod.GET)
                .handler(this::testResource)
                .handler(TimeoutHandler.create(1000, 504));
        router.route()
                .pathRegex(".*")
                .method(HttpMethod.GET)
                .handler(this::testResource)
                .handler(TimeoutHandler.create(1000, 504));
    }


    private void testResource(RoutingContext routingContext) {
        UriInfo uriInfo = getUriInfo(routingContext);
        HttpHeaders headers = getHeaders(routingContext);
        headers.getRequestHeaders()
                .putSingle(RevolversHttpHeaders.REQUEST_ID_HEADER, "123");
        headers.getRequestHeaders()
                .putSingle(RevolversHttpHeaders.TXN_ID_HEADER, "123");
        try {
            revolverRequestResource.processRequest("foxtrot", RequestMethod.GET, routingContext.normalisedPath(),
                    headers, uriInfo, routingContext.getBody() == null ? null : routingContext.getBody()
                            .getBytes());
        } catch (Exception e) {
            //Do nothing
        }
        AtomicReference<String> body = new AtomicReference<>();
        routingContext.response()
                .bodyEndHandler(buffer -> {
                    body.set(buffer.toString());
                });
        System.out.println("Execution completed :" + body.get());
        routingContext.response()
                .end(body.get());
    }

    private HttpHeaders getHeaders(RoutingContext routingContext) {

        try {
            return new ContainerRequest(new URI(routingContext.normalisedPath()),
                    new URI(routingContext.normalisedPath()), RequestMethod.GET.name(), null,
                    new MapPropertiesDelegate());
        } catch (URISyntaxException e) {
            log.error("Error while getting headers : ", e);
        }
        return null;
    }

    private UriInfo getUriInfo(RoutingContext routingContext) {

        UriInfo uriInfo = new org.glassfish.jersey.server.internal.routing.RoutingContext() {
            @Override
            public void pushMatchResult(MatchResult matchResult) {

            }

            @Override
            public void pushMatchedResource(Object resource) {

            }

            @Override
            public Object peekMatchedResource() {
                return null;
            }

            @Override
            public void pushTemplates(UriTemplate resourceTemplate, UriTemplate methodTemplate) {

            }

            @Override
            public String getFinalMatchingGroup() {
                return null;
            }

            @Override
            public void pushLeftHandPath() {

            }

            @Override
            public void setEndpoint(Endpoint endpoint) {
                this.setEndpoint(endpoint);
            }

            @Override
            public Endpoint getEndpoint() {
                return this.getEndpoint();
            }

            @Override
            public void setMatchedResourceMethod(ResourceMethod resourceMethod) {

            }

            @Override
            public void pushMatchedLocator(ResourceMethod resourceLocator) {

            }

            @Override
            public void pushMatchedRuntimeResource(RuntimeResource runtimeResource) {

            }

            @Override
            public void pushLocatorSubResource(Resource subResourceFromLocator) {

            }

            @Override
            public void setMappedThrowable(Throwable throwable) {

            }

            @Override
            public Method getResourceMethod() {
                return null;
            }

            @Override
            public Class<?> getResourceClass() {
                return null;
            }

            @Override
            public Throwable getMappedThrowable() {
                return null;
            }

            @Override
            public List<MatchResult> getMatchedResults() {
                return null;
            }

            @Override
            public List<UriTemplate> getMatchedTemplates() {
                return null;
            }

            @Override
            public List<PathSegment> getPathSegments(String name) {
                return null;
            }

            @Override
            public List<PathSegment> getPathSegments(String name, boolean decode) {
                return null;
            }

            @Override
            public List<RuntimeResource> getMatchedRuntimeResources() {
                return null;
            }

            @Override
            public ResourceMethod getMatchedResourceMethod() {
                return null;
            }

            @Override
            public Resource getMatchedModelResource() {
                return null;
            }

            @Override
            public List<ResourceMethod> getMatchedResourceLocators() {
                return null;
            }

            @Override
            public List<Resource> getLocatorSubResources() {
                return null;
            }

            @Override
            public String getPath() {
                return routingContext.normalisedPath();
            }

            @Override
            public String getPath(boolean decode) {
                return null;
            }

            @Override
            public List<PathSegment> getPathSegments() {
                return null;
            }

            @Override
            public List<PathSegment> getPathSegments(boolean decode) {
                return null;
            }

            @Override
            public URI getRequestUri() {
                return null;
            }

            @Override
            public UriBuilder getRequestUriBuilder() {
                return null;
            }

            @Override
            public URI getAbsolutePath() {
                return null;
            }

            @Override
            public UriBuilder getAbsolutePathBuilder() {
                return null;
            }

            @Override
            public URI getBaseUri() {
                return null;
            }

            @Override
            public UriBuilder getBaseUriBuilder() {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getPathParameters() {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getPathParameters(boolean decode) {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getQueryParameters() {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
                return null;
            }

            @Override
            public List<String> getMatchedURIs() {
                return null;
            }

            @Override
            public List<String> getMatchedURIs(boolean decode) {
                return null;
            }

            @Override
            public List<Object> getMatchedResources() {
                return null;
            }

            @Override
            public URI resolve(URI uri) {
                return null;
            }

            @Override
            public URI relativize(URI uri) {
                return null;
            }
        };
        return uriInfo;
    }

    private void testVertexResource(RoutingContext routingContext) {

        try {
            Thread.sleep(3000);
            System.out.println("Hi");
        } catch (InterruptedException e) {
            log.error("Thread interrupted", e);
        }
        System.out.println("Execution completed for vertx");
        routingContext.response()
                .setStatusCode(202)
                .end("Hello");
    }

}
