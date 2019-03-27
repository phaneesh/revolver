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

package io.dropwizard.revolver.persistence;

import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.base.core.RevolverCallbackResponses;
import io.dropwizard.revolver.base.core.RevolverRequestState;

import java.util.List;

/**
 * @author phaneesh
 */
public interface PersistenceProvider {

    boolean exists(final String requestId);

    void saveRequest(final String requestId, final String mailboxId, final RevolverCallbackRequest request);

    void saveRequest(final String requestId, final String mailboxId, final RevolverCallbackRequest request, final int ttl) throws Exception;

    void setRequestState(final String requestId, RevolverRequestState state, final int ttl) throws Exception;

    void saveResponse(final String requestId, RevolverCallbackResponse response, final int ttl) throws Exception;

    RevolverRequestState requestState(final String requestId);

    RevolverCallbackResponse response(final String requestId);

    List<RevolverCallbackResponses> responses(final String mailboxId);

    RevolverCallbackRequest request(final String requestId);

    List<RevolverCallbackRequest> requests(final String mailboxId);

}
