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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UnrecoverableErrors")
class UnrecoverableErrorsTest {

    @Test
    @DisplayName("rethrows OutOfMemoryError")
    void rethrowsOutOfMemoryError() {
        OutOfMemoryError oom = new OutOfMemoryError("simulated");

        assertThatThrownBy(() -> UnrecoverableErrors.rethrowIfUnrecoverable(oom))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated");
    }

    @Test
    @DisplayName("rethrows StackOverflowError")
    void rethrowsStackOverflowError() {
        StackOverflowError soe = new StackOverflowError("simulated");

        assertThatThrownBy(() -> UnrecoverableErrors.rethrowIfUnrecoverable(soe))
                .isInstanceOf(StackOverflowError.class)
                .hasMessage("simulated");
    }

    @Test
    @DisplayName("does not rethrow AssertionError")
    void doesNotRethrowAssertionError() {
        UnrecoverableErrors.rethrowIfUnrecoverable(new AssertionError("expected true"));
    }

    @Test
    @DisplayName("does not rethrow ThreadDeath")
    void doesNotRethrowThreadDeath() {
        UnrecoverableErrors.rethrowIfUnrecoverable(new ThreadDeath());
    }

    @Test
    @DisplayName("does not rethrow custom Error subclass")
    void doesNotRethrowCustomErrorSubclass() {
        class CustomError extends Error {
            CustomError(String message) {
                super(message);
            }
        }

        UnrecoverableErrors.rethrowIfUnrecoverable(new CustomError("custom"));
    }

    @Test
    @DisplayName("does not rethrow RuntimeException")
    void doesNotRethrowRuntimeException() {
        UnrecoverableErrors.rethrowIfUnrecoverable(new RuntimeException("runtime"));
    }

    @Test
    @DisplayName("does not rethrow null")
    void doesNotRethrowNull() {
        UnrecoverableErrors.rethrowIfUnrecoverable(null);
    }
}
