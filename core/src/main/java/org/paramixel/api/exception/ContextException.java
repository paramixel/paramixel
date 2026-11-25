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
 * Thrown when a named context entry cannot be supplied to the requesting occurrence.
 *
 * <p>The message names the entry, the occurrence path, and — for a type mismatch — both the stored and the
 * requested type, so a wrong key or a wrong type is diagnosed precisely rather than surfacing as a {@code null}
 * or a {@link ClassCastException}.
 *
 * <p>This is a framework exception: it is not a test failure. A hook that catches it is free to recover, which
 * is what {@link org.paramixel.api.Context#find(String, Class)} exists for.
 */
public final class ContextException extends ParamixelException {

    private static final long serialVersionUID = 1L;

    /** The entry name that was requested. */
    private final transient String name;

    /** The occurrence path that requested the entry. */
    private final transient String path;

    /**
     * Creates an exception.
     *
     * @param name the requested entry name; must not be {@code null}
     * @param path the occurrence path that requested the entry; must not be {@code null}
     * @param message the description; must not be {@code null}
     */
    public ContextException(final String name, final String path, final String message) {
        super(message);
        this.name = Objects.requireNonNull(name, "name is null");
        this.path = Objects.requireNonNull(path, "path is null");
    }

    /**
     * Returns the requested entry name.
     *
     * @return the name; never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the occurrence path that requested the entry.
     *
     * @return the path; never {@code null}
     */
    public String path() {
        return path;
    }
}
