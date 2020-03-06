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

package io.dropwizard.revolver.callback;

import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.persistence.PersistenceProvider;

public abstract class CallbackHandler {

    protected PersistenceProvider persistenceProvider;

    protected RevolverConfig revolverConfig;

    public CallbackHandler(PersistenceProvider persistenceProvider,
            RevolverConfig revolverConfig) {
        this.persistenceProvider = persistenceProvider;
        this.revolverConfig = revolverConfig;
    }

    public abstract void handle(String requestId, RevolverCallbackResponse response);
}
