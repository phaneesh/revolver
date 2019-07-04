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
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Singleton;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author phaneesh
 */
@Singleton
public class InMemoryPersistenceProvider implements PersistenceProvider {

    private final ConcurrentHashMap<String, RevolverCallbackRequest> callbackRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RevolverCallbackResponse> callbackResponse = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RevolverRequestState> callbackStates = new ConcurrentHashMap<>();
    private final MultivaluedMap<String, String> mailbox = new MultivaluedHashMap<>();

    @Override
    public boolean exists(String requestId) {
        return callbackRequests.containsKey(requestId);
    }

    @Override
    public void saveRequest(final String requestId, final String mailBoxId, final RevolverCallbackRequest request) {
        callbackRequests.put(requestId, request);
        if (!StringUtils.isBlank(mailBoxId)) mailbox.add(mailBoxId, requestId);
        callbackStates.put(requestId, RevolverRequestState.RECEIVED);
    }

    @Override
    public void saveRequest(final String requestId, final String mailBoxId, final RevolverCallbackRequest request, final int ttl) {
        callbackRequests.put(requestId, request);
        if (!StringUtils.isBlank(mailBoxId)) mailbox.add(mailBoxId, requestId);
        callbackStates.put(requestId, RevolverRequestState.RECEIVED);
    }

    @Override
    public void setRequestState(final String requestId, final RevolverRequestState state, final int ttl) {
        callbackStates.put(requestId, state);
    }

    @Override
    public void saveResponse(final String requestId, final RevolverCallbackResponse response, final int ttl) {
        callbackResponse.put(requestId, response);
        callbackStates.put(requestId, RevolverRequestState.RESPONDED);
    }

    @Override
    public RevolverRequestState requestState(final String requestId) {
        return callbackStates.get(requestId);
    }

    @Override
    public RevolverCallbackRequest request(final String requestId) {
        return callbackRequests.get(requestId);
    }

    @Override
    public RevolverCallbackResponse response(final String requestId) {
        return callbackResponse.get(requestId);
    }

    @Override
    public List<RevolverCallbackRequest> requests(final String mailboxId) {
        val requestIds = mailbox.get(mailboxId);
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            return requestIds.stream().filter(callbackRequests::containsKey).map(callbackRequests::get).collect(Collectors.toList());
        }
    }

    @Override
    public List<RevolverCallbackResponses> responses(final String mailboxId) {
        val requestIds = mailbox.get(mailboxId);
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            return requestIds.stream().filter(callbackResponse::containsKey).map(callbackResponse::get).map(e -> RevolverCallbackResponses.builder().headers(e.getHeaders()).statusCode(e.getStatusCode()).body(Base64.getEncoder().encodeToString(e.getBody())).build()).collect(Collectors.toList());
        }
    }
}
