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
 * Signals that an action should be marked as {@link org.paramixel.api.Status#ABORTED aborted}
 * rather than executed or failed.
 *
 * <p>This indicates a <em>precondition or assumption failure</em> — the action could not run
 * due to an unmet condition. This is distinct from a deliberate
 * {@link org.paramixel.api.exception.SkipException skip} (the action chose not to run) and an
 * unexpected {@link org.paramixel.api.exception.FailException failure} (the action ran but an
 * assertion did not hold).
 *
 * <p>The runtime catches {@code AbortException} and records the descriptor as
 * {@link org.paramixel.api.Status#ABORTED}. An abort does not, by itself, cancel sibling or
 * subsequent work: in a {@link org.paramixel.api.action.Sequential} or a
 * {@link org.paramixel.api.action.Parallel} an aborted child does not short-circuit the
 * surrounding action — remaining children still run, and the aggregate surfaces
 * {@link org.paramixel.api.Status#ABORTED}. (To stop the remaining children of a dependent
 * {@link org.paramixel.api.action.Sequential},
 * throw {@link org.paramixel.api.exception.FailException} instead.) The one exception is a
 * {@link org.paramixel.api.action.Loop}: there an
 * aborted iteration is treated as a request to stop iterating, so remaining iterations are
 * skipped and the action reports {@link org.paramixel.api.Status#ABORTED}.
 *
 * <p>This exception can be instantiated directly through {@link #AbortException(String)},
 * {@link #AbortException(String, Throwable)}, or the factory methods {@link #abort()} and
 * {@link #abort(String)}, which return the exception so that it can be thrown at the call site
 * ({@code throw abort("x")}).
 */
public final class AbortException extends RuntimeException {

    /**
     * Creates an aborted exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public AbortException(final String message) {
        super(Objects.requireNonNull(message, "message is null"));
        Arguments.requireNonBlank(message, "message is blank");
    }

    /**
     * Creates an aborted exception with the supplied message and cause.
     *
     * @param message the exception message
     * @param cause the cause of the abort; must not be {@code null}
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public AbortException(final String message, final Throwable cause) {
        super(Objects.requireNonNull(message, "message is null"), Objects.requireNonNull(cause, "cause is null"));
        Arguments.requireNonBlank(message, "message is blank");
    }

    /**
     * Returns an aborted exception with the default message {@code "aborted"}.
     *
     * <p>Use it as {@code throw abort()}.
     *
     * @return the aborted exception; never {@code null}
     */
    public static AbortException abort() {
        return new AbortException("aborted");
    }

    /**
     * Returns an aborted exception with the supplied message.
     *
     * <p>Use it as {@code throw abort("x")}.
     *
     * @param message the exception message
     * @return the aborted exception; never {@code null}
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static AbortException abort(final String message) {
        return new AbortException(message);
    }

    /**
     * Returns an aborted exception with the supplied message and cause.
     *
     * <p>Use it as {@code throw abort("x", cause)}.
     *
     * @param message the exception message
     * @param cause the cause of the abort; must not be {@code null}
     * @return the aborted exception; never {@code null}
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static AbortException abort(final String message, final Throwable cause) {
        return new AbortException(message, cause);
    }
}
