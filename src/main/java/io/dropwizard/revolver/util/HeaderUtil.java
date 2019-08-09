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

package io.dropwizard.revolver.util;

import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import java.util.Collections;
import java.util.List;

/**
 * @author phaneesh
 */
public interface HeaderUtil {

    static int getTTL(RevolverCallbackRequest callbackRequest) {
        List<String> ttl = callbackRequest.getHeaders()
                .getOrDefault(RevolversHttpHeaders.MAILBOX_TTL_HEADER, Collections.emptyList());
        int mailboxTtl = -1;
        if (!ttl.isEmpty()) {
            mailboxTtl = Integer.parseInt(ttl.get(0));
        }
        return mailboxTtl;
    }
}
