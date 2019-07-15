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

package io.dropwizard.revolver.core.util;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import io.dropwizard.revolver.core.RevolverExecutionException;
import io.dropwizard.revolver.core.model.RevolverRequest;
import io.dropwizard.revolver.core.tracing.TraceInfo;
import org.slf4j.MDC;

/**
 * @author phaneesh
 */
public class RevolverCommandHelper {

    public static String getName(RevolverRequest request) {
        return Joiner.on(".").join(request.getService(), request.getApi());
    }

    public static <T extends RevolverRequest> T normalize(T request) {
        if (null == request) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST,
                    "Request cannot be null");
        }
        TraceInfo traceInfo = request.getTrace();
        if (traceInfo == null) {
            traceInfo = new TraceInfo();
            request.setTrace(traceInfo);
        }
        if (Strings.isNullOrEmpty(traceInfo.getRequestId())) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST,
                    "Request ID must be passed in span");
        }
        if (Strings.isNullOrEmpty(traceInfo.getTransactionId())) {
            throw new RevolverExecutionException(RevolverExecutionException.Type.BAD_REQUEST,
                    "Transaction ID must be passed");
        }
        if (0L == traceInfo.getTimestamp()) {
            traceInfo.setTimestamp(System.currentTimeMillis());
        }
        return request;
    }

    public static void addContextInfo(String command, TraceInfo traceInfo) {
        MDC.put("command", command);
        MDC.put("transactionId", traceInfo.getTransactionId());
        MDC.put("requestId", traceInfo.getRequestId());
        MDC.put("parentRequestId", traceInfo.getParentRequestId());
    }

    public static void removeContextInfo() {
        MDC.remove("command");
        MDC.remove("requestId");
        MDC.remove("transactionId");
        MDC.remove("parentRequestId");
    }

}
