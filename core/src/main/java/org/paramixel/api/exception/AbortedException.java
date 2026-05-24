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
import org.paramixel.api.internal.support.Arguments;

/**
 * Signals that an action should be marked as aborted rather than executed or failed.
 *
 * <p>This indicates a precondition or assumption failure — the action could not run due to an
 * unmet condition, which is distinct from a deliberate skip or an unexpected failure.
 * The Paramixel runtime catches {@code AbortedException} and records the action as aborted
 * with the provided message, continuing execution of remaining actions. This exception can
 * be instantiated directly through {@link #AbortedException(String)} or thrown immediately
 * with {@link #abort()} and {@link #abort(String)}.
 */
public final class AbortedException extends RuntimeException {

    /**
     * Creates an aborted exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public AbortedException(final String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        Arguments.requireNonBlank(message, "message must not be blank");
    }

    /**
     * Throws an aborted exception with the default message {@code "aborted"}.
     *
     * @throws AbortedException always
     */
    public static void abort() {
        throw new AbortedException("aborted");
    }

    /**
     * Throws an aborted exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     * @throws AbortedException always
     */
    public static void abort(final String message) {
        throw new AbortedException(Objects.requireNonNull(message, "message must not be null"));
    }
}
