package io.dropwizard.revolver.routes;

import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig.RequestMethod;
import io.vertx.ext.web.RoutingContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;

/***
 Created by nitish.goyal on 16/03/20
 ***/
@Slf4j
public class RequestUtil {


    static UriInfo getUriInfo(RoutingContext routingContext) {

        return new UriInfo() {
            @Override
            public String getPath() {
                return routingContext.normalisedPath();
            }

            @Override
            public String getPath(boolean decode) {
                return routingContext.normalisedPath();
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
    }

    static HttpHeaders getHeaders(RoutingContext routingContext) {
        HttpHeaders headers = null;
        try {
            headers = new ContainerRequest(new URI(routingContext.normalisedPath()),
                    new URI(routingContext.normalisedPath()), RequestMethod.GET.name(), null,
                    new MapPropertiesDelegate());
        } catch (URISyntaxException e) {
            log.error("Error while getting headers : ", e);
        }
        if (headers == null) {
            return null;
        }
        if (headers.getRequestHeaders()
                .getFirst(RevolversHttpHeaders.TXN_ID_HEADER) == null) {
            headers.getRequestHeaders()
                    .putSingle(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                            .toString());
        }

        if (headers.getRequestHeaders()
                .getFirst(RevolversHttpHeaders.REQUEST_ID_HEADER) == null) {
            headers.getRequestHeaders()
                    .putSingle(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                            .toString());
        }

        return headers;
    }

    static String getServiceName(RoutingContext routingContext) {
        String path = routingContext.normalisedPath();
        int length = "/apis/".length();
        String servicePath = path.substring(length);
        int indexOfApiSlash = servicePath.indexOf('/');
        return servicePath.substring(0, indexOfApiSlash);
    }

    static String getApiPath(RoutingContext routingContext) {
        String path = routingContext.normalisedPath();
        int length = "/apis/".length();
        String servicePath = path.substring(length);
        int indexOfApiSlash = servicePath.indexOf('/');
        return servicePath.substring(indexOfApiSlash + 1);
    }

    static byte[] getBody(RoutingContext routingContext) {
        return routingContext.getBody() == null ? null : routingContext.getBody()
                .getBytes();
    }


}
