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

package io.dropwizard.revolver.http.model;

import com.google.common.base.Objects;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author phaneesh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ApiPathMap {

    private RevolverHttpApiConfig api;

    private String path;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiPathMap)) {
            return false;
        }
        ApiPathMap that = (ApiPathMap) o;
        return Objects.equal(api.getApi(), that.api.getApi()) &&
                Objects.equal(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(api.getApi(), path);
    }
}
