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

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.*;
import com.aerospike.client.task.IndexTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.dropwizard.revolver.aeroapike.AerospikeConnectionManager;
import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.base.core.RevolverCallbackResponses;
import io.dropwizard.revolver.base.core.RevolverRequestState;
import io.dropwizard.revolver.core.config.AerospikeMailBoxConfig;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.internal.util.collection.StringKeyIgnoreCaseMultivaluedMap;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author phaneesh
 */
@Slf4j
public class AeroSpikePersistenceProvider implements PersistenceProvider {


    private static final String IDX_MAILBOX_ID = "idx_mailbox_id";
    private static final String MAILBOX_SET_NAME = "mailbox_messages";
    private static final String DEFAULT_MAILBOX_ID = "NONE";
    private static final TypeReference<Map<String, List<String>>> headerAndQueryParamTypeReference = new TypeReference<Map<String, List<String>>>() {
    };
    private final AerospikeMailBoxConfig mailBoxConfig;
    private final ObjectMapper objectMapper;

    public AeroSpikePersistenceProvider(AerospikeMailBoxConfig mailBoxConfig,
                                        ObjectMapper objectMapper) {
        this.mailBoxConfig = mailBoxConfig;
        this.objectMapper = objectMapper;
        try {
            IndexTask idxMailboxId = AerospikeConnectionManager.getClient()
                    .createIndex(null, mailBoxConfig.getNamespace(), MAILBOX_SET_NAME,
                            IDX_MAILBOX_ID, BinNames.MAILBOX_ID, IndexType.STRING);
            idxMailboxId.waitTillComplete();
            IndexTask idxMessageState = AerospikeConnectionManager.getClient()
                    .createIndex(null, mailBoxConfig.getNamespace(), MAILBOX_SET_NAME,
                            "idx_message_state", BinNames.STATE, IndexType.STRING);
            idxMailboxId.waitTillComplete();
            idxMessageState.waitTillComplete();
        } catch (AerospikeException e) {
            log.warn("Failed to create indexes: Error Code - {} | Message: {}", e.getResultCode(),
                    e.getMessage());
        }
    }

    @Override
    public boolean exists(String requestId) {
        Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        return AerospikeConnectionManager.getClient()
                .exists(AerospikeConnectionManager.readPolicy, key);
    }

    @Override
    public void saveRequest(String requestId, String mailboxId, RevolverCallbackRequest request,
                            int ttl) throws Exception {
        Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        try {
            Bin service = new Bin(BinNames.SERVICE, request.getService());
            Bin api = new Bin(BinNames.API, request.getApi());
            Bin mode = new Bin(BinNames.MODE, request.getMode().toUpperCase());
            Bin method = new Bin(BinNames.METHOD,
                    Strings.isNullOrEmpty(request.getMethod()) ? null
                            : request.getMethod().toUpperCase());
            Bin path = new Bin(BinNames.PATH, request.getPath());
            Bin mailBoxId = new Bin(BinNames.MAILBOX_ID,
                    mailboxId == null ? DEFAULT_MAILBOX_ID : mailboxId);
            Bin queryParams = new Bin(BinNames.QUERY_PARAMS,
                    objectMapper.writeValueAsString(request.getQueryParams()));
            Bin callbackUri = new Bin(BinNames.CALLBACK_URI, request.getCallbackUri());
            Bin requestHeaders = new Bin(BinNames.REQUEST_HEADERS,
                    objectMapper.writeValueAsString(request.getHeaders()));
            Bin requestBody = new Bin(BinNames.REQUEST_BODY, request.getBody());
            Bin requestTime = new Bin(BinNames.REQUEST_TIME, Instant.now().toEpochMilli());
            Bin created = new Bin(BinNames.CREATED, Instant.now().toEpochMilli());
            Bin updated = new Bin(BinNames.UPDATED, Instant.now().toEpochMilli());
            Bin state = new Bin(BinNames.STATE, RevolverRequestState.RECEIVED.name());
            WritePolicy wp = ttl <= 0 ? AerospikeConnectionManager.writePolicy
                    : AerospikeConnectionManager.getWritePolicy(ttl);
            AerospikeConnectionManager.getClient()
                    .put(wp, key, service, api, mode, method, path, mailBoxId, queryParams,
                            callbackUri, requestHeaders, requestBody, requestTime, created, updated,
                            state);
            log.info("Mailbox Message saved. Key: {} | TTL: {}", requestId, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Error encoding request", e);
        }
    }

    @Override
    public void saveRequest(String requestId, String mailboxId, RevolverCallbackRequest request) {
        Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        try {
            Bin service = new Bin(BinNames.SERVICE, request.getService());
            Bin api = new Bin(BinNames.API, request.getApi());
            Bin mode = new Bin(BinNames.MODE, request.getMode().toUpperCase());
            Bin method = new Bin(BinNames.METHOD,
                    Strings.isNullOrEmpty(request.getMethod()) ? null
                            : request.getMethod().toUpperCase());
            Bin path = new Bin(BinNames.PATH, request.getPath());
            Bin mailBoxId = new Bin(BinNames.MAILBOX_ID,
                    mailboxId == null ? DEFAULT_MAILBOX_ID : mailboxId);
            Bin queryParams = new Bin(BinNames.QUERY_PARAMS,
                    objectMapper.writeValueAsString(request.getQueryParams()));
            Bin callbackUri = new Bin(BinNames.CALLBACK_URI, request.getCallbackUri());
            Bin requestHeaders = new Bin(BinNames.REQUEST_HEADERS,
                    objectMapper.writeValueAsString(request.getHeaders()));
            Bin requestBody = new Bin(BinNames.REQUEST_BODY, request.getBody());
            Bin requestTime = new Bin(BinNames.REQUEST_TIME, Instant.now().toEpochMilli());
            Bin created = new Bin(BinNames.CREATED, Instant.now().toEpochMilli());
            Bin updated = new Bin(BinNames.UPDATED, Instant.now().toEpochMilli());
            Bin state = new Bin(BinNames.STATE, RevolverRequestState.RECEIVED.name());
            AerospikeConnectionManager.getClient()
                    .put(AerospikeConnectionManager.writePolicy, key, service, api, mode, method,
                            path, mailBoxId, queryParams, callbackUri, requestHeaders, requestBody,
                            requestTime, created, updated, state);
        } catch (JsonProcessingException e) {
            log.warn("Error encoding request", e);
        }
    }

    @Override
    public void setRequestState(String requestId, RevolverRequestState state, int ttl)
            throws Exception {
        Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        Record record = AerospikeConnectionManager.getClient()
                .get(AerospikeConnectionManager.readPolicy, key, BinNames.STATE);
        RevolverRequestState requestState = RevolverRequestState
                .valueOf(record.getString(BinNames.STATE));
        if (requestState != RevolverRequestState.RESPONDED) {
            WritePolicy wp = ttl <= 0 ? AerospikeConnectionManager.writePolicy
                    : AerospikeConnectionManager.getWritePolicy(ttl);
            Bin binState = new Bin(BinNames.STATE, state.name());
            Bin updated = new Bin(BinNames.UPDATED, Instant.now().toEpochMilli());
            AerospikeConnectionManager.getClient()
                    .operate(wp, key, Operation.put(binState), Operation.put(updated));
        }
    }

    @Override
    public void saveResponse(String requestId, RevolverCallbackResponse response, int ttl)
            throws Exception {
        long start = System.currentTimeMillis();
        Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        Bin state = new Bin(BinNames.STATE, RevolverRequestState.RESPONDED.name());
        try {
            Bin responseHeaders = new Bin(BinNames.RESPONSE_HEADERS,
                    objectMapper.writeValueAsString(response.getHeaders()));
            Bin responseBody = new Bin(BinNames.RESPONSE_BODY, response.getBody());
            Bin responseStatusCode = new Bin(BinNames.RESPONSE_STATUS_CODE,
                    response.getStatusCode());
            Bin responseTime = new Bin(BinNames.RESPONSE_TIME, Instant.now().toEpochMilli());
            Bin updated = new Bin(BinNames.UPDATED, Instant.now().toEpochMilli());
            WritePolicy wp = ttl <= 0 ? AerospikeConnectionManager.writePolicy
                    : AerospikeConnectionManager.getWritePolicy(ttl);
            AerospikeConnectionManager.getClient()
                    .operate(wp, key, Operation.put(state), Operation.put(responseHeaders),
                            Operation.put(responseBody), Operation.put(responseStatusCode),
                            Operation.put(responseTime), Operation.put(updated));
            log.info("Response save complete for request id: {} in {} ms", requestId,
                    (System.currentTimeMillis() - start));
        } catch (JsonProcessingException e) {
            log.warn("Error encoding response headers", e);
        }
    }

    @Override
    public RevolverRequestState requestState(String requestId) {
        return requestState(requestId, null, false);
    }

    @Override
    public RevolverRequestState requestState(String requestId, String mailBoxId) {
        return requestState(requestId, mailBoxId, true);
    }

    private RevolverRequestState requestState(String requestId, String mailBoxId, boolean enforceMailboxIdCheck) {
        Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        Record record = AerospikeConnectionManager.getClient()
                .get(AerospikeConnectionManager.readPolicy, key);
        if (record == null || isInvalidMailboxId(enforceMailboxIdCheck, mailBoxId, record)) {
            return RevolverRequestState.UNKNOWN;
        }
        return RevolverRequestState.valueOf(record.getString(BinNames.STATE));
    }

    @Override
    public RevolverCallbackResponse response(String requestId, String mailBoxId) {
        Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        Record record = AerospikeConnectionManager.getClient()
                .get(AerospikeConnectionManager.readPolicy, key);
        if (record == null || isInvalidMailboxId(true, mailBoxId, record)) {
            return null;
        }
        return recordToResponse(record);
    }

    @Override
    public List<RevolverCallbackResponses> responses(String mailboxId) {
        Statement statement = new Statement();
        statement.setNamespace(mailBoxConfig.getNamespace());
        statement.setSetName(MAILBOX_SET_NAME);
        statement.setIndexName(IDX_MAILBOX_ID);
        statement.setFilter(Filter.equal(BinNames.MAILBOX_ID, mailboxId));
        List<RevolverCallbackResponses> responses = new ArrayList<>();
        try (RecordSet records = AerospikeConnectionManager.getClient().query(null, statement)) {
            while (records.next()) {
                Record record = records.getRecord();

                RevolverRequestState state = RevolverRequestState
                        .valueOf(record.getString(BinNames.STATE));
                if (state == RevolverRequestState.ERROR
                        || state == RevolverRequestState.RESPONDED) {
                    responses.add(recordToResponses(record, records.getKey()));
                }
            }
        }
        return responses;
    }

    @Override
    public RevolverCallbackRequest request(String requestId) {
        return request(requestId, null, false);
    }

    @Override
    public RevolverCallbackRequest request(String requestId, String mailBoxId) {
        return request(requestId, mailBoxId, true);
    }

    public RevolverCallbackRequest request(String requestId, String mailBoxId, boolean enforceMailboxIdCheck) {
        long start = System.currentTimeMillis();
        Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        Record record = AerospikeConnectionManager.getClient()
                .get(AerospikeConnectionManager.readPolicy, key);
        if (record == null || isInvalidMailboxId(enforceMailboxIdCheck, mailBoxId, record)) {
            return null;
        }
        RevolverCallbackRequest request = recordToRequest(record);
        log.info("Callback request fetch for request id: {} complete in {} ms", requestId,
                (System.currentTimeMillis() - start));
        return request;
    }

    /**
     * Checks mailbox id saved earlier in record against mailbox id coming in access request
     *
     * @param enforceMailboxIdCheck : flag to enforce mailbox id check
     * @param mailBoxId             :  mailbox id in access request
     * @param record                : Aerospike record
     * @return boolean
     */
    private boolean isInvalidMailboxId(boolean enforceMailboxIdCheck, String mailBoxId, Record record) {
        return enforceMailboxIdCheck && !DEFAULT_MAILBOX_ID.equals(record.getString(BinNames.MAILBOX_ID))
                && !record.getString(BinNames.MAILBOX_ID).equals(mailBoxId);
    }

    @Override
    public List<RevolverCallbackRequest> requests(String mailboxId) {
        Statement statement = new Statement();
        statement.setNamespace(mailBoxConfig.getNamespace());
        statement.setSetName(MAILBOX_SET_NAME);
        statement.setIndexName(IDX_MAILBOX_ID);
        statement.setFilter(Filter.equal(BinNames.MAILBOX_ID, mailboxId));
        List<RevolverCallbackRequest> requests = new ArrayList<>();
        try (RecordSet records = AerospikeConnectionManager.getClient().query(null, statement)) {
            while (records.next()) {
                requests.add(recordToRequest(records.getRecord()));
            }
        }
        return requests;
    }

    private RevolverCallbackRequest recordToRequest(Record record) {
        Map<String, List<String>> headers = new HashMap<>();
        Map<String, List<String>> queryParams = new HashMap<>();
        try {
            headers = objectMapper.readValue(record.getString(BinNames.REQUEST_HEADERS),
                    headerAndQueryParamTypeReference);
            queryParams = objectMapper.readValue(record.getString(BinNames.QUERY_PARAMS),
                    headerAndQueryParamTypeReference);
        } catch (IOException e) {
            log.warn("Error decoding response", e);
        }
        Map<String, List<String>> headersKeyIgnoreCaseMap = new StringKeyIgnoreCaseMultivaluedMap<>();
        Map<String, List<String>> queryParamsKeyIgnoreCaseMap = new StringKeyIgnoreCaseMultivaluedMap<>();
        headers.forEach(headersKeyIgnoreCaseMap::put);
        queryParams.forEach(queryParamsKeyIgnoreCaseMap::put);
        return RevolverCallbackRequest.builder().headers(headersKeyIgnoreCaseMap)
                .api(record.getString(BinNames.API))
                .callbackUri(record.getString(BinNames.CALLBACK_URI))
                .body(record.getValue(BinNames.REQUEST_BODY) == null ? null
                        : (byte[]) record.getValue(BinNames.REQUEST_BODY))
                .method(record.getString(BinNames.METHOD)).mode(record.getString(BinNames.MODE))
                .path(record.getString(BinNames.PATH)).queryParams(queryParamsKeyIgnoreCaseMap)
                .service(record.getString(BinNames.SERVICE)).build();
    }

    private RevolverCallbackResponse recordToResponse(Record record) {
        Map<String, List<String>> headers = new HashMap<>();
        try {
            headers = objectMapper.readValue(record.getString(BinNames.RESPONSE_HEADERS),
                    new TypeReference<Map<String, List<String>>>() {
                    });
        } catch (IOException e) {
            log.warn("Error decoding response headers", e);
        }
        return RevolverCallbackResponse.builder()
                .body((byte[]) record.getValue(BinNames.RESPONSE_BODY))
                .statusCode(record.getInt(BinNames.RESPONSE_STATUS_CODE)).headers(headers).build();
    }

    private RevolverCallbackResponses recordToResponses(Record record, Key key) {
        Map<String, List<String>> headers = new HashMap<>();
        try {
            headers = objectMapper.readValue(record.getString(BinNames.RESPONSE_HEADERS),
                    new TypeReference<Map<String, List<String>>>() {
                    });
        } catch (IOException e) {
            log.warn("Error decoding response headers", e);
        }
        return RevolverCallbackResponses.builder().body(Base64.getEncoder()
                .encodeToString((byte[]) (record.getValue(BinNames.RESPONSE_BODY))))
                .statusCode(record.getInt(BinNames.RESPONSE_STATUS_CODE)).headers(headers)
                .requestId((String) key.userKey.getObject()).build();
    }

    private abstract static class BinNames {

        static final String MAILBOX_ID = "mailbox_id";
        static final String SERVICE = "service";
        static final String API = "api";
        static final String MODE = "mode";
        static final String METHOD = "method";
        static final String PATH = "path";
        static final String QUERY_PARAMS = "query_params";
        static final String CALLBACK_URI = "callback_uri";
        static final String REQUEST_HEADERS = "req_headers";
        static final String REQUEST_BODY = "req_body";
        static final String REQUEST_TIME = "req_time";
        static final String RESPONSE_HEADERS = "resp_headers";
        static final String RESPONSE_BODY = "resp_body";
        static final String RESPONSE_TIME = "resp_time";
        static final String RESPONSE_STATUS_CODE = "resp_code";
        static final String CREATED = "created";
        static final String UPDATED = "updated";
        static final String STATE = "state";

        private BinNames() {
        }

    }
}
