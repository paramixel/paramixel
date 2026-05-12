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

package org.paramixel.core.internal;

/**
 * Internal utilities for working with <em>unrecoverable</em> errors.
 *
 * <p>Unrecoverable errors are those that should always terminate execution immediately because the JVM
 * cannot meaningfully continue. All other {@link Error} subtypes are treated as recoverable failures and
 * captured in results rather than rethrown.
 *
 * <p>Currently unrecoverable errors:
 * <ul>
 * <li>{@link OutOfMemoryError}</li>
 * <li>{@link StackOverflowError}</li>
 * </ul>
 */
public final class UnrecoverableErrors {

    private UnrecoverableErrors() {
        // Intentionally empty
    }

    /**
     * Rethrows the supplied throwable if it is an unrecoverable error.
     *
     * <p>If the supplied throwable is not an unrecoverable error, this method does nothing.
     *
     * @param throwable the throwable to inspect, or {@code null}
     * @throws OutOfMemoryError if the throwable is an {@code OutOfMemoryError}
     * @throws StackOverflowError if the throwable is a {@code StackOverflowError}
     */
    public static void rethrowIfUnrecoverable(Throwable throwable) {
        if (throwable instanceof OutOfMemoryError || throwable instanceof StackOverflowError) {
            throw (Error) throwable;
        }
    }
}
