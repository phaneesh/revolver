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

import com.aerospike.client.*;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
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
import java.util.*;

/**
 * @author phaneesh
 */
@Slf4j
public class AeroSpikePersistenceProvider implements PersistenceProvider {


    private static final String IDX_MAILBOX_ID = "idx_mailbox_id";

    private abstract static class BinNames {

        private BinNames() {}

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

    }

    private final AerospikeMailBoxConfig mailBoxConfig;

    private final ObjectMapper objectMapper;

    private static final String MAILBOX_SET_NAME = "mailbox_messages";

    public AeroSpikePersistenceProvider(AerospikeMailBoxConfig mailBoxConfig, final ObjectMapper objectMapper) {
        this.mailBoxConfig = mailBoxConfig;
        this.objectMapper = objectMapper;
        try {
            final IndexTask idxMailboxId = AerospikeConnectionManager.getClient().createIndex(null, mailBoxConfig.getNamespace(), MAILBOX_SET_NAME,
                    IDX_MAILBOX_ID, BinNames.MAILBOX_ID, IndexType.STRING);
            idxMailboxId.waitTillComplete();
            final IndexTask idxMessageState = AerospikeConnectionManager.getClient().createIndex(null, mailBoxConfig.getNamespace(), MAILBOX_SET_NAME,
                    "idx_message_state", BinNames.STATE, IndexType.STRING);
            idxMailboxId.waitTillComplete();
            idxMessageState.waitTillComplete();
        } catch (AerospikeException e) {
            log.warn("Failed to create indexes: Error Code - {} | Message: {}", e.getResultCode(), e.getMessage());
        }
    }

    @Override
    public boolean exists(String requestId) {
        final Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        return AerospikeConnectionManager.getClient().exists(AerospikeConnectionManager.readPolicy, key);
    }

    @Override
    public void saveRequest(String requestId, String mailboxId, RevolverCallbackRequest request, int ttl) throws Exception {
        final Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        try {
            final Bin service = new Bin(BinNames.SERVICE, request.getService());
            final Bin api = new Bin(BinNames.API, request.getApi());
            final Bin mode = new Bin(BinNames.MODE, request.getMode().toUpperCase());
            final Bin method = new Bin(BinNames.METHOD, Strings.isNullOrEmpty(request.getMethod()) ? null : request.getMethod().toUpperCase());
            final Bin path = new Bin(BinNames.PATH, request.getPath());
            final Bin mailBoxId = new Bin(BinNames.MAILBOX_ID, mailboxId == null ? "NONE" : mailboxId);
            final Bin queryParams = new Bin(BinNames.QUERY_PARAMS, objectMapper.writeValueAsString(request.getQueryParams()));
            final Bin callbackUri = new Bin(BinNames.CALLBACK_URI, request.getCallbackUri());
            final Bin requestHeaders = new Bin(BinNames.REQUEST_HEADERS, objectMapper.writeValueAsString(request.getHeaders()));
            final Bin requestBody = new Bin(BinNames.REQUEST_BODY, request.getBody());
            final Bin requestTime = new Bin(BinNames.REQUEST_TIME, Instant.now().toEpochMilli());
            final Bin created = new Bin(BinNames.CREATED, Instant.now().toEpochMilli());
            final Bin updated = new Bin(BinNames.UPDATED, Instant.now().toEpochMilli());
            final Bin state = new Bin(BinNames.STATE, RevolverRequestState.RECEIVED.name());
            WritePolicy wp = ttl <= 0 ? AerospikeConnectionManager.writePolicy : AerospikeConnectionManager.getWritePolicy(ttl);
            AerospikeConnectionManager.getClient().put(wp, key,
                    service, api, mode, method, path, mailBoxId, queryParams, callbackUri, requestHeaders, requestBody, requestTime,
                    created, updated, state);
            log.info("Mailbox Message saved. Key: {} | TTL: {}", requestId, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Error encoding request", e);
        }
    }

    @Override
    public void saveRequest(String requestId, String mailboxId, RevolverCallbackRequest request) {
        final Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        try {
            final Bin service = new Bin(BinNames.SERVICE, request.getService());
            final Bin api = new Bin(BinNames.API, request.getApi());
            final Bin mode = new Bin(BinNames.MODE, request.getMode().toUpperCase());
            final Bin method = new Bin(BinNames.METHOD, Strings.isNullOrEmpty(request.getMethod()) ? null : request.getMethod().toUpperCase());
            final Bin path = new Bin(BinNames.PATH, request.getPath());
            final Bin mailBoxId = new Bin(BinNames.MAILBOX_ID, mailboxId == null ? "NONE" : mailboxId);
            final Bin queryParams = new Bin(BinNames.QUERY_PARAMS, objectMapper.writeValueAsString(request.getQueryParams()));
            final Bin callbackUri = new Bin(BinNames.CALLBACK_URI, request.getCallbackUri());
            final Bin requestHeaders = new Bin(BinNames.REQUEST_HEADERS, objectMapper.writeValueAsString(request.getHeaders()));
            final Bin requestBody = new Bin(BinNames.REQUEST_BODY, request.getBody());
            final Bin requestTime = new Bin(BinNames.REQUEST_TIME, Instant.now().toEpochMilli());
            final Bin created = new Bin(BinNames.CREATED, Instant.now().toEpochMilli());
            final Bin updated = new Bin(BinNames.UPDATED, Instant.now().toEpochMilli());
            final Bin state = new Bin(BinNames.STATE, RevolverRequestState.RECEIVED.name());
            AerospikeConnectionManager.getClient().put(AerospikeConnectionManager.writePolicy, key,
                    service, api, mode, method, path, mailBoxId, queryParams, callbackUri, requestHeaders, requestBody, requestTime,
                    created, updated, state);
        } catch (JsonProcessingException e) {
            log.warn("Error encoding request", e);
        }
    }

    @Override
    public void setRequestState(String requestId, RevolverRequestState state, int ttl) throws Exception {
        final Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        final Record record = AerospikeConnectionManager.getClient().get(AerospikeConnectionManager.readPolicy, key, BinNames.STATE);
        final RevolverRequestState requestState = RevolverRequestState.valueOf(record.getString(BinNames.STATE));
        if (requestState != RevolverRequestState.RESPONDED) {
            WritePolicy wp = ttl <= 0 ? AerospikeConnectionManager.writePolicy : AerospikeConnectionManager.getWritePolicy(ttl);
            final Bin binState = new Bin(BinNames.STATE, state.name());
            final Bin updated = new Bin(BinNames.UPDATED, Instant.now().toEpochMilli());
            AerospikeConnectionManager.getClient().operate(wp, key,
                    Operation.put(binState), Operation.put(updated));
        }
    }

    @Override
    public void saveResponse(String requestId, RevolverCallbackResponse response, final int ttl) throws Exception {
        long start = System.currentTimeMillis();
        final Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        final Bin state = new Bin(BinNames.STATE, RevolverRequestState.RESPONDED.name());
        try {
            final Bin responseHeaders = new Bin(BinNames.RESPONSE_HEADERS, objectMapper.writeValueAsString(response.getHeaders()));
            final Bin responseBody = new Bin(BinNames.RESPONSE_BODY, response.getBody());
            final Bin responseStatusCode = new Bin(BinNames.RESPONSE_STATUS_CODE, response.getStatusCode());
            final Bin responseTime = new Bin(BinNames.RESPONSE_TIME, Instant.now().toEpochMilli());
            final Bin updated = new Bin(BinNames.UPDATED, Instant.now().toEpochMilli());
            WritePolicy wp = ttl <= 0 ? AerospikeConnectionManager.writePolicy : AerospikeConnectionManager.getWritePolicy(ttl);
            AerospikeConnectionManager.getClient().operate(wp, key,
                    Operation.put(state),
                    Operation.put(responseHeaders),
                    Operation.put(responseBody),
                    Operation.put(responseStatusCode),
                    Operation.put(responseTime),
                    Operation.put(updated));
            log.info("Response save complete for request id: {} in {} ms", requestId, (System.currentTimeMillis() - start));
        } catch (JsonProcessingException e) {
            log.warn("Error encoding response headers", e);
        }
    }

    @Override
    public RevolverRequestState requestState(String requestId) {
        final Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        final Record record = AerospikeConnectionManager.getClient().get(AerospikeConnectionManager.readPolicy, key, BinNames.STATE);
        if(record == null) {
            return RevolverRequestState.UNKNOWN;
        }
        return RevolverRequestState.valueOf(record.getString(BinNames.STATE));
    }

    @Override
    public RevolverCallbackResponse response(String requestId) {
        final Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        final Record record = AerospikeConnectionManager.getClient().get(AerospikeConnectionManager.readPolicy, key);
        if(record == null) {
            return null;
        }
        return recordToResponse(record);
    }

    @Override
    public List<RevolverCallbackResponses> responses(String mailboxId) {
        final Statement statement = new Statement();
        statement.setNamespace(mailBoxConfig.getNamespace());
        statement.setSetName(MAILBOX_SET_NAME);
        statement.setIndexName(IDX_MAILBOX_ID);
        statement.setFilters(Filter.equal(BinNames.MAILBOX_ID, mailboxId));
        List<RevolverCallbackResponses> responses = new ArrayList<>();
        try (RecordSet records = AerospikeConnectionManager.getClient().query(null, statement)) {
            while (records.next()) {
                Record record =  records.getRecord();

                RevolverRequestState state = RevolverRequestState.valueOf(record.getString(BinNames.STATE));
                if (state == RevolverRequestState.ERROR || state == RevolverRequestState.RESPONDED) {
                    responses.add(recordToResponses(record, records.getKey()));
                }
            }
        }
        return responses;
    }

    @Override
    public RevolverCallbackRequest request(String requestId) {
        long start = System.currentTimeMillis();
        final Key key = new Key(mailBoxConfig.getNamespace(), MAILBOX_SET_NAME, requestId);
        final Record record = AerospikeConnectionManager.getClient().get(AerospikeConnectionManager.readPolicy, key);
        if(record == null) {
            return null;
        }
        RevolverCallbackRequest request = recordToRequest(record);
        log.info("Callback request fetch for request id: {} complete in {} ms", requestId, (System.currentTimeMillis() - start));
        return request;
    }

    @Override
    public List<RevolverCallbackRequest> requests(String mailboxId) {
        final Statement statement = new Statement();
        statement.setNamespace(mailBoxConfig.getNamespace());
        statement.setSetName(MAILBOX_SET_NAME);
        statement.setIndexName(IDX_MAILBOX_ID);
        statement.setFilters(Filter.equal(BinNames.MAILBOX_ID, mailboxId));
        List<RevolverCallbackRequest> requests = new ArrayList<>();
        try (RecordSet records = AerospikeConnectionManager.getClient().query(null, statement)) {
            while (records.next()) {
                requests.add(recordToRequest(records.getRecord()));
            }
        }
        return requests;
    }

    private static final  TypeReference<Map<String, List<String>>> headerAndQueryParamTypeReference = new TypeReference<Map<String, List<String>>>(){};

    private RevolverCallbackRequest recordToRequest(Record record) {
        Map<String, List<String>> headers = new HashMap<>();
        Map<String, List<String>> queryParams = new HashMap<>();
        try {
            headers = objectMapper.readValue(record.getString(BinNames.REQUEST_HEADERS), headerAndQueryParamTypeReference);
            queryParams = objectMapper.readValue(record.getString(BinNames.QUERY_PARAMS), headerAndQueryParamTypeReference);
        } catch (IOException e) {
            log.warn("Error decoding response", e);
        }
        Map<String, List<String>> headersKeyIgnoreCaseMap = new StringKeyIgnoreCaseMultivaluedMap<>();
        Map<String, List<String>> queryParamsKeyIgnoreCaseMap = new StringKeyIgnoreCaseMultivaluedMap<>();
        headers.forEach(headersKeyIgnoreCaseMap::put);
        queryParams.forEach(queryParamsKeyIgnoreCaseMap::put);
        return RevolverCallbackRequest.builder()
                .headers(headersKeyIgnoreCaseMap)
                .api(record.getString(BinNames.API))
                .callbackUri(record.getString(BinNames.CALLBACK_URI))
                .body(record.getValue(BinNames.REQUEST_BODY) == null ? null : (byte[])record.getValue(BinNames.REQUEST_BODY))
                .method(record.getString(BinNames.METHOD))
                .mode(record.getString(BinNames.MODE))
                .path(record.getString(BinNames.PATH))
                .queryParams(queryParamsKeyIgnoreCaseMap)
                .service(record.getString(BinNames.SERVICE))
                .build();
    }

    private RevolverCallbackResponse recordToResponse(Record record) {
        Map<String, List<String>> headers = new HashMap<>();
        try {
            headers = objectMapper.readValue(record.getString(BinNames.RESPONSE_HEADERS), new TypeReference<Map<String, List<String>>>(){});
        } catch (IOException e) {
            log.warn("Error decoding response headers", e);
        }
        return RevolverCallbackResponse.builder()
                .body((byte[])record.getValue(BinNames.RESPONSE_BODY))
                .statusCode(record.getInt(BinNames.RESPONSE_STATUS_CODE))
                .headers(headers)
                .build();
    }

    private RevolverCallbackResponses recordToResponses(Record record, Key key) {
        Map<String, List<String>> headers = new HashMap<>();
        try {
            headers = objectMapper.readValue(record.getString(BinNames.RESPONSE_HEADERS), new TypeReference<Map<String, List<String>>>(){});
        } catch (IOException e) {
            log.warn("Error decoding response headers", e);
        }
        return RevolverCallbackResponses.builder()
                .body(Base64.getEncoder().encodeToString((byte[])(record.getValue(BinNames.RESPONSE_BODY))))
                .statusCode(record.getInt(BinNames.RESPONSE_STATUS_CODE))
                .headers(headers)
                .requestId((String)key.userKey.getObject())
                .build();
    }
}
