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
 * Signals that a task's assumption or precondition did not hold, so it should be marked
 * {@link org.paramixel.api.Status.Kind#ABORTED} instead of executed or failed.
 *
 * <p>An abort is distinct from a deliberate {@link SkipException} (the task chose not to run) and from a
 * {@link FailException} (the task ran and its assertion did not hold). The runtime records the occurrence as
 * {@code ABORTED} and never reclassifies it as a passing skip.
 *
 * <p>An abort does not cancel the run and is never retried automatically. In a dependent sequential group an aborted
 * child stops later siblings, exactly like a failure; in an independent sequential group or a parallel group the
 * remaining eligible children still run.
 *
 * <p>Use the factory methods {@link #abort()} and {@link #abort(String)} at the call site:
 * {@code throw abort("service not available")}.
 */
public final class AbortException extends ParamixelException {

    private static final long serialVersionUID = 1L;

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
