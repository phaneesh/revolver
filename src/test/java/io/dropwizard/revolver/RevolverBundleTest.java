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

package io.dropwizard.revolver;

import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author phaneesh
 */
@Slf4j
public class RevolverBundleTest extends BaseRevolverTest {


    @Test
    public void testBootstrap() {
        assertTrue(bundle.getPersistenceProvider(configuration, environment) instanceof InMemoryPersistenceProvider);
        assertNotNull(bundle.getRevolverConfig(configuration));
        assertNotNull(RevolverBundle.matchPath("test", "v1/test"));
        assertNotNull(RevolverBundle.getHttpCommand("test", "test"));
    }


}
