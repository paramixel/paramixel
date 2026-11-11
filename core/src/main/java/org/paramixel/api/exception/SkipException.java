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
 * Signals that an action should be marked as {@link org.paramixel.api.Status#SKIPPED skipped}
 * rather than executed or failed.
 *
 * <p>This is a deliberate, author-initiated skip. The runtime catches {@code SkipException}
 * and records the descriptor as {@link org.paramixel.api.Status#SKIPPED}. Effect on
 * surrounding actions depends on the container: in a dependent
 * {@link org.paramixel.api.action.Sequential} a
 * skipped child short-circuits the sequence (remaining children are skipped); in an
 * independent {@link org.paramixel.api.action.Sequential} or
 * a {@link org.paramixel.api.action.Parallel},
 * remaining children still run; and in a {@link org.paramixel.api.action.Loop}
 * a skipped iteration does not stop the loop. This
 * exception can be instantiated directly through {@link #SkipException(String)},
 * {@link #SkipException(String, Throwable)}, or the factory methods {@link #skip()} and
 * {@link #skip(String)}, which return the exception so that it can be thrown at the call site
 * ({@code throw skip("x")}).
 */
public final class SkipException extends RuntimeException {

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
