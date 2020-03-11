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

package io.dropwizard.revolver.http;

/**
 * @author phaneesh
 */
public interface RevolversHttpHeaders {

    String TXN_ID_HEADER = "X-TRANSACTION-ID";
    String REQUEST_ID_HEADER = "X-REQUEST-ID";
    String PARENT_REQUEST_ID_HEADER = "X-PARENT-REQUEST-ID";
    String TIMESTAMP_HEADER = "X-REQUEST-TIMESTAMP";
    String CLIENT_HEADER = "X-CLIENT-ID";
    String CALL_MODE_HEADER = "X-CALL-MODE";
    String MAILBOX_ID_HEADER = "X-MAILBOX-ID";
    String MAILBOX_AUTH_ID_HEADER = "X-MAILBOX-AUTH-ID";
    String MAILBOX_TTL_HEADER = "X-MAILBOX-TTL";
    String CALLBACK_URI_HEADER = "X-CALLBACK-URI";
    String CALLBACK_TIMEOUT_HEADER = "X-CALLBACK-TIMEOUT";
    String CALLBACK_METHOD_HEADER = "X-CALLBACK-METHOD";
    String CALLBACK_RESPONSE_CODE = "X-RESPONSE-CODE";
    String RETRY_AFTER = "X-RETRY-AFTER";
    String DYAMIC_MAILBOX = "X-DYNAMIC-MAILBOX";
    String FORWARDED_BY = "X-FORWARDED-BY";

}
