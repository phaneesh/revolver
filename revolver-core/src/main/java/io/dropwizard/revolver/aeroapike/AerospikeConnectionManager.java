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

package io.dropwizard.revolver.aeroapike;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.client.policy.CommitLevel;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.ReadModeAP;
import com.aerospike.client.policy.Replica;
import com.aerospike.client.policy.WritePolicy;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.dropwizard.revolver.core.config.AerospikeMailBoxConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * @author phaneesh
 */
@Slf4j
public class AerospikeConnectionManager {

    public static WritePolicy writePolicy;
    public static Policy readPolicy;
    private static IAerospikeClient client;
    private static AerospikeMailBoxConfig config;
    private static LoadingCache<Integer, WritePolicy> writePolicyCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<Integer, WritePolicy>() {
                @Override
                public WritePolicy load(Integer key) {
                    WritePolicy wp = new WritePolicy();
                    wp.maxRetries = config.getRetries();
                    wp.readModeAP = ReadModeAP.ONE;
                    wp.replica = Replica.MASTER_PROLES;
                    wp.sleepBetweenRetries = config.getSleepBetweenRetries();
                    wp.commitLevel = CommitLevel.COMMIT_ALL;
                    wp.totalTimeout = config.getTimeout();
                    wp.sendKey = true;
                    wp.expiration = key;
                    return wp;
                }
            });

    private AerospikeConnectionManager() {
    }

    public static void init(AerospikeMailBoxConfig aerospikeConfig) {
        config = aerospikeConfig;

        readPolicy = new Policy();
        readPolicy.maxRetries = config.getRetries();
        readPolicy.readModeAP = ReadModeAP.ONE;
        readPolicy.replica = Replica.MASTER_PROLES;
        readPolicy.sleepBetweenRetries = config.getSleepBetweenRetries();
        readPolicy.totalTimeout = config.getTimeout();
        readPolicy.sendKey = true;

        writePolicy = new WritePolicy();
        writePolicy.maxRetries = config.getRetries();
        writePolicy.readModeAP = ReadModeAP.ONE;
        writePolicy.replica = Replica.MASTER_PROLES;
        writePolicy.sleepBetweenRetries = config.getSleepBetweenRetries();
        writePolicy.commitLevel = CommitLevel.COMMIT_ALL;
        writePolicy.totalTimeout = config.getTimeout();
        writePolicy.sendKey = true;
        writePolicy.expiration = config.getTtl();

        val clientPolicy = new ClientPolicy();
        clientPolicy.maxConnsPerNode = config.getMaxConnectionsPerNode();
        clientPolicy.readPolicyDefault = readPolicy;
        clientPolicy.writePolicyDefault = writePolicy;
        clientPolicy.failIfNotConnected = true;
        clientPolicy.threadPool = Executors.newFixedThreadPool(64);
        clientPolicy.connPoolsPerNode = config.getMaxConnectionsPerNode();
        clientPolicy.sharedThreadPool = true;

        val hosts = config.getHosts().split(",");
        client = new AerospikeClient(clientPolicy, Arrays.stream(hosts).map(h -> {
            String[] host = h.split(":");
            if (host.length == 2) {
                return new Host(host[0], Integer.parseInt(host[1]));
            } else {
                return new Host(host[0], 3000);
            }
        }).toArray(Host[]::new));
        log.info("Aerospike connection status: " + client.isConnected());
    }

    public static IAerospikeClient getClient() {
        Preconditions.checkNotNull(client);
        return client;
    }

    public static void setClient(IAerospikeClient aerospikeClient) {
        client = aerospikeClient;
    }

    public static void close() {
        if (null != client) {
            client.close();
        }
    }

    public static WritePolicy getWritePolicy(int ttl) throws ExecutionException {
        return writePolicyCache.get(ttl);
    }

}
