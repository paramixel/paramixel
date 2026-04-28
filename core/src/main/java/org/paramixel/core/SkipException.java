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

package org.paramixel.core;

/**
 * Signals that an action should be marked as skipped.
 */
public class SkipException extends RuntimeException {

    /**
     * Creates a skip signal without a detail message.
     */
    public SkipException() {
        super();
    }

    /**
     * Creates a skip signal with a detail message.
     *
     * @param message The detail message; may be null.
     */
    public SkipException(final String message) {
        super(message);
    }

    /**
     * Creates a skip signal with a detail message and cause.
     *
     * @param message The detail message; may be null.
     * @param cause The cause; may be null.
     */
    public SkipException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Throws a skip signal without a detail message.
     *
     * @throws SkipException Always thrown.
     */
    public static void skip() {
        throw new SkipException();
    }

    /**
     * Throws a skip signal with a detail message.
     *
     * @param message The detail message; may be null.
     * @throws SkipException Always thrown.
     */
    public static void skip(final String message) {
        throw new SkipException(message);
    }

    /**
     * Throws a skip signal with a detail message and cause.
     *
     * @param message The detail message; may be null.
     * @param cause The cause; may be null.
     * @throws SkipException Always thrown.
     */
    public static void skip(final String message, final Throwable cause) {
        throw new SkipException(message, cause);
    }
}
