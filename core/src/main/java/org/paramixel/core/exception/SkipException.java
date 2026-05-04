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
 * Signals that an action should be marked as skipped.
 *
 * <p>This exception can be instantiated directly through {@link #of(String)} or thrown immediately with
 * {@link #skip()} and {@link #skip(String)}.
 */
public final class SkipException extends RuntimeException {

    private SkipException(final String message) {
        super(message);
    }

    /**
     * Creates a skip exception with the supplied message.
     *
     * @param message the exception message
     * @return a new skip exception
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     */
    public static SkipException of(String message) {
        Objects.requireNonNull(message, "message must not be null");
        Arguments.requireNonBlank(message, "message must not be blank");
        return new SkipException(message);
    }

    /**
     * Throws a skip exception with the default message {@code "skipped"}.
     *
     * @throws SkipException always
     */
    public static void skip() {
        throw new SkipException("skipped");
    }

    /**
     * Throws a skip exception with the supplied message.
     *
     * @param message the exception message
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if {@code message} is blank
     * @throws SkipException always
     */
    public static void skip(final String message) {
        Objects.requireNonNull(message, "message must not be null");
        Arguments.requireNonBlank(message, "message must not be blank");
        throw new SkipException(message);
    }
}
