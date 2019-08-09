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

package io.dropwizard.revolver.core;

/**
 * @author phaneesh
 */
public class RevolverExecutionException extends RuntimeException {

    private static final long serialVersionUID = -8567678723466161055L;
    private final Type type;

    public RevolverExecutionException(Type type) {
        this.type = type;
    }

    public RevolverExecutionException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public RevolverExecutionException(Type type, String message,
            Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public RevolverExecutionException(Type type, Throwable cause) {
        super(cause);
        this.type = type;
    }

    public Type getType() {
        return this.type;
    }

    public enum Type {
        SERVICE_ERROR, DOWNSTREAM_SERVICE_CALL_FAILURE, BAD_REQUEST;

        Type() {
        }
    }
}
