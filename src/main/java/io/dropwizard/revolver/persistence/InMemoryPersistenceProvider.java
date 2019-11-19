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

import com.google.common.base.Strings;
import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.base.core.RevolverCallbackResponses;
import io.dropwizard.revolver.base.core.RevolverRequestState;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import lombok.val;
import org.apache.commons.lang3.StringUtils;

/**
 * @author phaneesh
 */
@Singleton
public class InMemoryPersistenceProvider implements PersistenceProvider {

    private final ConcurrentHashMap<String, RevolverCallbackRequest> callbackRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RevolverCallbackResponse> callbackResponse = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RevolverRequestState> callbackStates = new ConcurrentHashMap<>();
    private final MultivaluedMap<String, String> mailbox = new MultivaluedHashMap<>();
    private final ConcurrentHashMap<String, String> requestToMailboxMap = new ConcurrentHashMap<>();

    @Override
    public boolean exists(String requestId) {
        return callbackRequests.containsKey(requestId);
    }

    @Override
    public void saveRequest(String requestId, String mailBoxId,
                            RevolverCallbackRequest request) {
        callbackRequests.put(requestId, request);
        if (!StringUtils.isBlank(mailBoxId)) {
            mailbox.add(mailBoxId, requestId);
            requestToMailboxMap.put(requestId, mailBoxId);
        }
        callbackStates.put(requestId, RevolverRequestState.RECEIVED);
    }

    @Override
    public void saveRequest(String requestId, String mailBoxId,
                            RevolverCallbackRequest request, int ttl) {
        callbackRequests.put(requestId, request);
        if (!StringUtils.isBlank(mailBoxId)) {
            mailbox.add(mailBoxId, requestId);
            requestToMailboxMap.put(requestId, mailBoxId);
        }
        callbackStates.put(requestId, RevolverRequestState.RECEIVED);
    }

    @Override
    public void setRequestState(String requestId, RevolverRequestState state,
                                int ttl) {
        callbackStates.put(requestId, state);
    }

    @Override
    public void saveResponse(String requestId, RevolverCallbackResponse response,
                             int ttl) {
        callbackResponse.put(requestId, response);
        callbackStates.put(requestId, RevolverRequestState.RESPONDED);
    }

    @Override
    public RevolverRequestState requestState(String requestId) {
        return callbackStates.get(requestId);
    }

    @Override
    public RevolverCallbackRequest request(String requestId) {
        return callbackRequests.get(requestId);
    }

    @Override
    public RevolverRequestState requestState(String requestId, String mailBoxId) {
        if (isInvalidMailboxId(requestId, mailBoxId)) {
            return RevolverRequestState.UNKNOWN;
        }
        return callbackStates.get(requestId);
    }

    private boolean isInvalidMailboxId(String requestId, String mailBoxId) {
        return !Strings.isNullOrEmpty(requestToMailboxMap.get(requestId))
                && !requestToMailboxMap.get(requestId).equals(mailBoxId);
    }

    @Override
    public RevolverCallbackRequest request(String requestId, String mailBoxId) {
        if (isInvalidMailboxId(requestId, mailBoxId)) {
            return null;
        }
        return callbackRequests.get(requestId);
    }

    @Override
    public RevolverCallbackResponse response(String requestId, String mailBoxId) {
        if (isInvalidMailboxId(requestId, mailBoxId)) {
            return null;
        }
        return callbackResponse.get(requestId);
    }

    @Override
    public List<RevolverCallbackRequest> requests(String mailboxId) {
        val requestIds = mailbox.get(mailboxId);
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            return requestIds.stream().filter(callbackRequests::containsKey)
                    .map(callbackRequests::get).collect(Collectors.toList());
        }
    }

    @Override
    public List<RevolverCallbackResponses> responses(String mailboxId) {
        val requestIds = mailbox.get(mailboxId);
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            return requestIds.stream().filter(callbackResponse::containsKey)
                    .map(callbackResponse::get)
                    .map(e -> RevolverCallbackResponses.builder().headers(e.getHeaders())
                            .statusCode(e.getStatusCode())
                            .body(Base64.getEncoder().encodeToString(e.getBody())).build())
                    .collect(Collectors.toList());
        }
    }

}
