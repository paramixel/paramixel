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
 * Signals a deliberate, author-initiated failure.
 *
 * <p>Distinct from an uncaught exception only in intent: the runtime records either as
 * {@link org.paramixel.api.Status.Kind#FAILED} with the original cause, and cleanup still runs.
 *
 * <p>Use the factory methods {@link #fail()} and {@link #fail(String)} at the call site:
 * {@code throw fail("unexpected payload")}.
 */
public final class FailException extends ParamixelException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a failure exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public FailException(final String message) {
        super(Objects.requireNonNull(message, "message is null"));
        Arguments.requireNonBlank(message, "message is blank");
    }

    /**
     * Creates a failure exception with the supplied message and cause.
     *
     * @param message the exception message
     * @param cause the cause of the failure; must not be {@code null}
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public FailException(final String message, final Throwable cause) {
        super(Objects.requireNonNull(message, "message is null"), Objects.requireNonNull(cause, "cause is null"));
        Arguments.requireNonBlank(message, "message is blank");
    }

    /**
     * Returns a failure exception with the default message {@code "failed"}.
     *
     * <p>Use it as {@code throw fail()}; returning the exception rather than throwing it keeps the
     * call site explicit and helps the compiler's definite-assignment analysis.
     *
     * @return the failure exception; never {@code null}
     */
    public static FailException fail() {
        return new FailException("failed");
    }

    /**
     * Returns a failure exception with the supplied message.
     *
     * <p>Use it as {@code throw fail("x")}.
     *
     * @param message the exception message
     * @return the failure exception; never {@code null}
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static FailException fail(final String message) {
        return new FailException(message);
    }

    /**
     * Returns a failure exception with the supplied message and cause.
     *
     * <p>Use it as {@code throw fail("x", cause)}.
     *
     * @param message the exception message
     * @param cause the cause of the failure; must not be {@code null}
     * @return the failure exception; never {@code null}
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static FailException fail(final String message, final Throwable cause) {
        return new FailException(message, cause);
    }
}
