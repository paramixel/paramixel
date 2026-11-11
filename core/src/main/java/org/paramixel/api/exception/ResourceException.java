/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.paramixel.api.exception;

import java.util.Objects;

/**
 * Thrown when a keyed resource is not available to the requesting occurrence.
 *
 * <p>The message names the key, the occurrence path, and the reason, so a missing declaration is diagnosed precisely
 * rather than surfacing as a {@code null} or a {@link ClassCastException}.
 */
public final class ResourceException extends ParamixelException {

    private static final long serialVersionUID = 1L;

    /** The key that was requested. */
    private final transient org.paramixel.api.Key<?> key;

    /** The occurrence path that requested the resource. */
    private final String path;

    /**
     * Creates an exception.
     *
     * @param key the requested key; must not be {@code null}
     * @param path the occurrence path that requested the resource; must not be {@code null}
     * @param message the description; must not be {@code null}
     */
    public ResourceException(final org.paramixel.api.Key<?> key, final String path, final String message) {
        super(message);
        this.key = Objects.requireNonNull(key, "key is null");
        this.path = Objects.requireNonNull(path, "path is null");
    }

    /**
     * Returns the requested key.
     *
     * @return the key; never {@code null}
     */
    public org.paramixel.api.Key<?> key() {
        return key;
    }

    /**
     * Returns the requesting occurrence path.
     *
     * @return the path; never {@code null}
     */
    public String path() {
        return path;
    }
}
