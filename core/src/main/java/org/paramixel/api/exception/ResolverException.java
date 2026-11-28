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
import nonapi.org.paramixel.support.Arguments;

/**
 * Signals that factory discovery or factory resolution failed during classpath scanning.
 *
 * <p>Thrown when an annotated factory method is not {@code public static}, declares parameters, returns an
 * incompatible type, conflicts with another factory in the same hierarchy, or cannot be invoked. Discovery validates
 * every factory before any discovered test work executes, so this exception halts the run before user callbacks.
 */
public final class ResolverException extends ParamixelException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a resolver exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public ResolverException(final String message) {
        super(Objects.requireNonNull(message, "message is null"));
        Arguments.requireNonBlank(message, "message is blank");
    }

    /**
     * Creates a resolver exception with the supplied message and cause.
     *
     * @param message the exception message
     * @param cause the underlying cause
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public ResolverException(final String message, final Throwable cause) {
        super(Objects.requireNonNull(message, "message is null"), Objects.requireNonNull(cause, "cause is null"));
        Arguments.requireNonBlank(message, "message is blank");
    }
}
