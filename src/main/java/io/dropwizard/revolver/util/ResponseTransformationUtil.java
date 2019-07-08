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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.msgpack.MsgPackMediaType;
import java.io.IOException;
import javax.ws.rs.core.MediaType;

/**
 * @author phaneesh
 */
public interface ResponseTransformationUtil {

    static byte[] transform(Object response, String mediaType, ObjectMapper jsonObjectMapper,
            ObjectMapper msgPackObjectMapper) throws IOException {
        if (mediaType.startsWith(MediaType.APPLICATION_JSON)) {
            return jsonObjectMapper.writeValueAsBytes(response);
        }
        if (mediaType.startsWith(MsgPackMediaType.APPLICATION_MSGPACK)) {
            return msgPackObjectMapper.writeValueAsBytes(response);
        }
        return jsonObjectMapper.writeValueAsBytes(response);
    }
}
