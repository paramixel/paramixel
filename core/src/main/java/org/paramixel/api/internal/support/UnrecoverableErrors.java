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

package org.paramixel.api.internal.support;

/**
 * Rethrows non-{@code StackOverflowError} {@code VirtualMachineError} throwables immediately while all other
 * throwables are captured in action results.
 *
 * <p>Non-{@link StackOverflowError} {@link VirtualMachineError} throwables are unrecoverable. They indicate
 * JVM-level integrity or resource problems and should always terminate execution immediately. All other
 * throwables, including {@link StackOverflowError}, are treated as recoverable failures and captured in results
 * rather than rethrown.
 *
 * <p>Currently unrecoverable throwables:
 * <ul>
 * <li>{@link OutOfMemoryError}</li>
 * <li>{@link InternalError}</li>
 * <li>{@link UnknownError}</li>
 * <li>Any other non-{@link StackOverflowError} {@link VirtualMachineError} subclass</li>
 * </ul>
 *
 * <p>{@link StackOverflowError} is intentionally recoverable. It often represents a localized recursion bug in
 * user or test code. Once the stack unwinds to Paramixel's catch boundary, it should be reportable as an action
 * or test failure.
 */
public final class UnrecoverableErrors {

    private UnrecoverableErrors() {
        // Intentionally empty
    }

    /**
     * Returns whether the supplied throwable is an unrecoverable error.
     *
     * <p>Non-{@link StackOverflowError} {@link VirtualMachineError} throwables are unrecoverable. All other
     * throwables, including {@link StackOverflowError}, are recoverable.
     *
     * @param throwable the throwable to inspect, or {@code null}
     * @return {@code true} when the throwable is a non-{@code StackOverflowError}
     *     {@code VirtualMachineError}
     */
    public static boolean isUnrecoverable(final Throwable throwable) {
        return throwable instanceof VirtualMachineError && !(throwable instanceof StackOverflowError);
    }

    /**
     * Rethrows the supplied throwable if it is an unrecoverable error.
     *
     * <p>If the supplied throwable is not an unrecoverable error, this method does nothing.
     *
     * @param throwable the throwable to inspect, or {@code null}
     * @throws VirtualMachineError if the throwable is a non-{@code StackOverflowError}
     *     {@code VirtualMachineError}
     */
    public static void rethrowIfUnrecoverable(final Throwable throwable) {
        if (isUnrecoverable(throwable) && throwable instanceof Error error) {
            throw error;
        }
    }
}
