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

package nonapi.org.paramixel.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UnrecoverableErrors")
class UnrecoverableErrorsTest {

    @Test
    @DisplayName("OutOfMemoryError is unrecoverable")
    void outOfMemoryErrorIsUnrecoverable() {
        assertThat(UnrecoverableErrors.isUnrecoverable(new OutOfMemoryError("simulated")))
                .isTrue();
    }

    @Test
    @DisplayName("InternalError is unrecoverable")
    void internalErrorIsUnrecoverable() {
        assertThat(UnrecoverableErrors.isUnrecoverable(new InternalError("simulated")))
                .isTrue();
    }

    @Test
    @DisplayName("UnknownError is unrecoverable")
    void unknownErrorIsUnrecoverable() {
        assertThat(UnrecoverableErrors.isUnrecoverable(new UnknownError("simulated")))
                .isTrue();
    }

    @Test
    @DisplayName("StackOverflowError is recoverable")
    void stackOverflowErrorIsRecoverable() {
        assertThat(UnrecoverableErrors.isUnrecoverable(new StackOverflowError("simulated")))
                .isFalse();
    }

    @Test
    @DisplayName("AssertionError is recoverable")
    void assertionErrorIsRecoverable() {
        assertThat(UnrecoverableErrors.isUnrecoverable(new AssertionError("expected true")))
                .isFalse();
    }

    @Test
    @DisplayName("RuntimeException is recoverable")
    void runtimeExceptionIsRecoverable() {
        assertThat(UnrecoverableErrors.isUnrecoverable(new RuntimeException("runtime")))
                .isFalse();
    }

    @Test
    @DisplayName("checked Exception is recoverable")
    void checkedExceptionIsRecoverable() {
        assertThat(UnrecoverableErrors.isUnrecoverable(new Exception("checked")))
                .isFalse();
    }

    @Test
    @DisplayName("null is recoverable")
    void nullIsRecoverable() {
        assertThat(UnrecoverableErrors.isUnrecoverable(null)).isFalse();
    }

    @Test
    @DisplayName("rethrows OutOfMemoryError")
    void rethrowsOutOfMemoryError() {
        var oom = new OutOfMemoryError("simulated");

        assertThatThrownBy(() -> UnrecoverableErrors.rethrowIfUnrecoverable(oom))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("simulated");
    }

    @Test
    @DisplayName("rethrows InternalError")
    void rethrowsInternalError() {
        var ie = new InternalError("simulated");

        assertThatThrownBy(() -> UnrecoverableErrors.rethrowIfUnrecoverable(ie))
                .isInstanceOf(InternalError.class)
                .hasMessage("simulated");
    }

    @Test
    @DisplayName("does not rethrow StackOverflowError")
    void doesNotRethrowStackOverflowError() {
        UnrecoverableErrors.rethrowIfUnrecoverable(new StackOverflowError("simulated"));
    }

    @Test
    @DisplayName("does not rethrow AssertionError")
    void doesNotRethrowAssertionError() {
        UnrecoverableErrors.rethrowIfUnrecoverable(new AssertionError("expected true"));
    }

    @Test
    @DisplayName("does not rethrow ThreadDeath")
    @SuppressWarnings("removal")
    void doesNotRethrowThreadDeath() {
        UnrecoverableErrors.rethrowIfUnrecoverable(new ThreadDeath());
    }

    @Test
    @DisplayName("does not rethrow custom Error subclass")
    void doesNotRethrowCustomErrorSubclass() {
        class CustomError extends Error {
            CustomError(final String message) {
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
