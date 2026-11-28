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
 * Signals that a task deliberately does not run, so it should be marked
 * {@link org.paramixel.api.Status.Kind#SKIPPED} with a reason.
 *
 * <p>A skip is an author decision, not a failure and not an assumption abort. The runtime records the occurrence as
 * {@code SKIPPED}; whether that fails a build is a run-level policy
 * ({@link org.paramixel.api.Configuration#FAILURE_ON_SKIP}), not a task decision. A skipped child does not count as a
 * passing child when a parent aggregates its children.
 *
 * <p>Use the factory methods {@link #skip()} and {@link #skip(String)} at the call site:
 * {@code throw skip("no credentials configured")}.
 */
public final class SkipException extends ParamixelException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a skip exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public SkipException(final String message) {
        super(Objects.requireNonNull(message, "message is null"));
        Arguments.requireNonBlank(message, "message is blank");
    }

    /**
     * Creates a skip exception with the supplied message and cause.
     *
     * @param message the exception message
     * @param cause the cause of the skip; must not be {@code null}
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public SkipException(final String message, final Throwable cause) {
        super(Objects.requireNonNull(message, "message is null"), Objects.requireNonNull(cause, "cause is null"));
        Arguments.requireNonBlank(message, "message is blank");
    }

    /**
     * Returns a skip exception with the default message {@code "skipped"}.
     *
     * <p>Use it as {@code throw skip()}.
     *
     * @return the skip exception; never {@code null}
     */
    public static SkipException skip() {
        return new SkipException("skipped");
    }

    /**
     * Returns a skip exception with the supplied message.
     *
     * <p>Use it as {@code throw skip("x")}.
     *
     * @param message the exception message
     * @return the skip exception; never {@code null}
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static SkipException skip(final String message) {
        return new SkipException(message);
    }

    /**
     * Returns a skip exception with the supplied message and cause.
     *
     * <p>Use it as {@code throw skip("x", cause)}.
     *
     * @param message the exception message
     * @param cause the cause of the skip; must not be {@code null}
     * @return the skip exception; never {@code null}
     * @throws NullPointerException if {@code message} or {@code cause} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static SkipException skip(final String message, final Throwable cause) {
        return new SkipException(message, cause);
    }
}
