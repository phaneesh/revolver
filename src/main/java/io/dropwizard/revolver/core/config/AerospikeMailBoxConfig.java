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

package io.dropwizard.revolver.core.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author phaneesh
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class AerospikeMailBoxConfig extends MailBoxConfig {

    private String hosts;

    private String namespace;

    private int maxConnectionsPerNode;

    private int timeout;

    private int retries;

    private int sleepBetweenRetries;

    private int ttl;

    private String defaultMailboxId = "NONE";

    @Builder
    public AerospikeMailBoxConfig(final String hosts, final String namespace,
            final int maxConnectionsPerNode, final int timeout, final int retries,
            final int sleepBetweenRetries, final int ttl, final String defaultMailboxId) {
        super("aerospike");
        this.hosts = hosts;
        this.namespace = namespace;
        this.maxConnectionsPerNode = maxConnectionsPerNode;
        this.timeout = timeout;
        this.retries = retries;
        this.sleepBetweenRetries = sleepBetweenRetries;
        this.ttl = ttl;
        this.defaultMailboxId = defaultMailboxId;
    }

    //Default values
    public static class AerospikeMailBoxConfigBuilder {

        private int ttl = 10800;
    }
}
