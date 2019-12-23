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

    boolean exists(String requestId);

    void saveRequest(String requestId, String mailboxId, String mailboxAuthId,
            RevolverCallbackRequest request);

    void saveRequest(String requestId, String mailboxId, String mailboxAuthId,
            RevolverCallbackRequest request, int ttl) throws Exception;

    void setRequestState(String requestId, RevolverRequestState state, int ttl)
            throws Exception;

    void saveResponse(String requestId, RevolverCallbackResponse response, int ttl)
            throws Exception;

    RevolverRequestState requestState(String requestId);

    RevolverRequestState requestState(String requestId, String mailBoxAuthId);

    RevolverCallbackResponse response(String requestId, String mailBoxAuthId);

    RevolverCallbackRequest request(String requestId);

    RevolverCallbackRequest request(String requestId, String mailBoxAuthId);

    List<RevolverCallbackRequest> requestsByMailbox(String mailboxId);

    List<RevolverCallbackRequest> requestsByMailboxAuth(String mailboxAuthId);

    List<RevolverCallbackResponses> responsesByMailbox(String mailboxId);

    List<RevolverCallbackResponses> responsesByMailboxAuth(String mailboxAuthId);

}
