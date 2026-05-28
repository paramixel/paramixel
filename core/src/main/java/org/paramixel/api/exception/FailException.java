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
 * Signals that an action should be marked as failed rather than allowed to propagate an unexpected error.
 *
 * <p>This is a deliberate, author-initiated failure — distinct from an uncaught runtime exception.
 * The Paramixel runtime catches {@code FailException} and records the action as failed with the
 * provided message. This exception can be instantiated directly through {@link #FailException(String)} or
 * thrown immediately with {@link #fail()} and {@link #fail(String)}.
 */
public final class FailException extends RuntimeException {

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
     * Throws a failure exception with the default message {@code "failed"}.
     *
     * @throws FailException always
     */
    public static void fail() {
        throw new FailException("failed");
    }

    /**
     * Throws a failure exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     * @throws FailException always
     */
    public static void fail(final String message) {
        Objects.requireNonNull(message, "message is null");
        Arguments.requireNonBlank(message, "message is blank");
        throw new FailException(message);
    }
}
