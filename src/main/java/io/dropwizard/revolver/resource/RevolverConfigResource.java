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

package io.dropwizard.revolver.resource;

import com.codahale.metrics.annotation.Metered;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.revolver.handler.DynamicConfigHandler;
import io.swagger.annotations.ApiOperation;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("/revolver")
@Slf4j
@Data
@Singleton
public class RevolverConfigResource {

    private DynamicConfigHandler dynamicConfigHandler;

    @Builder
    public RevolverConfigResource(DynamicConfigHandler dynamicConfigHandler) {
        this.dynamicConfigHandler = dynamicConfigHandler;
    }

    @Path("/v1/config/reload")
    @POST
    @Metered
    @ApiOperation(value = "Reload revolver configuration")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reload() {
        long start = System.currentTimeMillis();
        String hash = dynamicConfigHandler.refreshConfig();
        long end = System.currentTimeMillis() - start;
        if (hash == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(ImmutableMap.<String, Object>builder().put("hash", hash).put("timeTaken", end).build()).build();
    }

    @Path("/v1/config/info")
    @GET
    @Metered
    @ApiOperation(value = "Revolver configuration reload info")
    @Produces(MediaType.APPLICATION_JSON)
    public Response info() {
        return Response.ok(dynamicConfigHandler.configLoadInfo()).build();
    }

}
