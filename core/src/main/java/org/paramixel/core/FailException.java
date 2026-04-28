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
 * Signals that an action should be marked as failed.
 */
public class FailException extends RuntimeException {

    /**
     * Creates a failure signal without a detail message.
     */
    public FailException() {
        super();
    }

    /**
     * Creates a failure signal with a detail message.
     *
     * @param message The detail message; may be null.
     */
    public FailException(final String message) {
        super(message);
    }

    /**
     * Creates a failure signal with a detail message and cause.
     *
     * @param message The detail message; may be null.
     * @param cause The cause; may be null.
     */
    public FailException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Throws a failure signal without a detail message.
     *
     * @throws FailException Always thrown.
     */
    public static void fail() {
        throw new FailException();
    }

    /**
     * Throws a failure signal with a detail message.
     *
     * @param message The detail message; may be null.
     * @throws FailException Always thrown.
     */
    public static void fail(final String message) {
        throw new FailException(message);
    }

    /**
     * Throws a failure signal with a detail message and cause.
     *
     * @param message The detail message; may be null.
     * @param cause The cause; may be null.
     * @throws FailException Always thrown.
     */
    public static void fail(final String message, final Throwable cause) {
        throw new FailException(message, cause);
    }
}
