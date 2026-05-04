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

package org.paramixel.core.exception;

import java.util.Objects;

/**
 * Indicates that action discovery or action-factory resolution failed.
 *
 * <p>This exception is used by resolver components when annotated factory methods are invalid, conflicting, or cannot
 * be invoked successfully.
 */
public final class ResolverException extends RuntimeException {

    private ResolverException(final String message) {
        super(message);
    }

    private ResolverException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a resolver exception with the supplied message.
     *
     * @param message the exception message
     * @return a new resolver exception
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public static ResolverException of(String message) {
        Objects.requireNonNull(message, "message must not be null");
        return new ResolverException(message);
    }

    /**
     * Creates a resolver exception with the supplied message and cause.
     *
     * @param message the exception message
     * @param cause the underlying cause
     * @return a new resolver exception
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     */
    public static ResolverException of(String message, Throwable cause) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(cause, "cause must not be null");
        return new ResolverException(message, cause);
    }
}
