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
import org.paramixel.core.support.Arguments;

/**
 * Signals that an action should be marked as failed.
 *
 * <p>This exception can be instantiated directly through {@link #of(String)} or thrown immediately with
 * {@link #fail()} and {@link #fail(String)}.
 */
public final class FailException extends RuntimeException {

    private FailException(final String message) {
        super(message);
    }

    /**
     * Creates a failure exception with the supplied message.
     *
     * @param message the exception message
     * @return a new failure exception
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static FailException of(String message) {
        return new FailException(Arguments.requireNonBlank(message, "message must not be blank"));
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
        throw new FailException(Arguments.requireNonBlank(
                Objects.requireNonNull(message, "message must not be null"), "message must not be blank"));
    }
}
