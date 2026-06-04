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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.exception.UserCodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("UserCodeException")
class UserCodeExceptionTest {

    @Nested
    @DisplayName("wrap()")
    class Wrap {

        @Test
        @DisplayName("returns null when input is null")
        void returnsNullWhenInputIsNull() {
            assertThat(UserCodeException.wrap(null)).isNull();
        }

        @Test
        @DisplayName("returns same instance when input is already a UserCodeException")
        void returnsSameInstanceWhenAlreadyWrapped() {
            var original = new UserCodeException(new IOException("test"));
            assertThat(UserCodeException.wrap(original)).isSameAs(original);
        }

        @Test
        @DisplayName("wraps RuntimeException cause in UserCodeException")
        void wrapsRuntimeExceptionCause() {
            var cause = new IOException("database connection failed");
            var runtimeException = new RuntimeException(cause);

            var result = UserCodeException.wrap(runtimeException);

            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isSameAs(cause);
            assertThat(result.getMessage()).isEqualTo("database connection failed");
        }

        @Test
        @DisplayName("wraps RuntimeException when cause is null")
        void wrapsRuntimeExceptionWhenCauseIsNull() {
            var runtimeException = new RuntimeException();
            var result = UserCodeException.wrap(runtimeException);
            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isSameAs(runtimeException);
        }

        @Test
        @DisplayName("wraps non-RuntimeException throwable")
        void wrapsNonRuntimeExceptionThrowable() {
            var ioException = new IOException("file not found");
            var result = UserCodeException.wrap(ioException);
            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isSameAs(ioException);
        }

        @Test
        @DisplayName("wraps plain Exception")
        void wrapsPlainException() {
            var exception = new Exception("generic error");
            var result = UserCodeException.wrap(exception);
            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isSameAs(exception);
        }

        @Test
        @DisplayName("preserves original message when wrapping IOException cause")
        void preservesOriginalMessageWhenWrappingIOException() {
            var cause = new IOException("connection reset");
            var runtimeException = new RuntimeException(cause);

            var result = UserCodeException.wrap(runtimeException);

            assertThat(result.getMessage()).isEqualTo("connection reset");
        }

        @Test
        @DisplayName("wraps SQLException cause")
        void wrapsSqlExceptionCause() {
            var cause = new SQLException("table not found");
            var runtimeException = new RuntimeException(cause);

            var result = UserCodeException.wrap(runtimeException);

            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("wraps custom checked exception cause")
        void wrapsCustomCheckedExceptionCause() {
            var cause = new CustomCheckedException("custom error");
            var runtimeException = new RuntimeException(cause);

            var result = UserCodeException.wrap(runtimeException);

            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("preserves exception chain for semantic exceptions")
        void preservesExceptionChainForSemanticExceptions() {
            var cause = new FailException("assertion failed");
            var runtimeException = new RuntimeException(cause);

            var result = UserCodeException.wrap(runtimeException);

            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("only unwraps one level - inner RuntimeException is preserved as cause")
        void onlyUnwrapsOneLevel() {
            var innerCause = new IOException("inner io error");
            var innerRuntime = new RuntimeException(innerCause);
            var outerRuntime = new RuntimeException(innerRuntime);

            var result = UserCodeException.wrap(outerRuntime);

            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isSameAs(innerRuntime);
        }

        @Test
        @DisplayName("can be called concurrently without issues")
        void canBeCalledConcurrently() throws InterruptedException {
            var runtimeException = new RuntimeException(new IOException("concurrent test"));
            var reference = new AtomicReference<Throwable>();

            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> reference.set(UserCodeException.wrap(runtimeException)));
                threads[i].start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            assertThat(reference.get()).isInstanceOf(UserCodeException.class);
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("stores cause and message correctly")
        void storesCauseAndMessageCorrectly() {
            var cause = new IOException("stored message");
            var exception = new UserCodeException(cause);

            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getMessage()).isEqualTo("stored message");
        }

        @Test
        @DisplayName("handles cause with null message")
        void handlesCauseWithNullMessage() {
            var cause = new IOException();
            var exception = new UserCodeException(cause);

            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getMessage()).isNull();
        }

        @Test
        @DisplayName("handles RuntimeException as cause")
        void handlesRuntimeExceptionAsCause() {
            var cause = new RuntimeException("runtime error");
            var exception = new UserCodeException(cause);

            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getMessage()).isEqualTo("runtime error");
        }

        @Test
        @DisplayName("handles Error as cause")
        void handlesErrorAsCause() {
            var cause = new AssertionError("assertion error");
            var exception = new UserCodeException(cause);

            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getMessage()).isEqualTo("assertion error");
        }

        @Test
        @DisplayName("handles semantic exceptions as cause")
        void handlesSemanticExceptionsAsCause() {
            var cause = new AbortedException("precondition failed");
            var exception = new UserCodeException(cause);

            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.getMessage()).isEqualTo("precondition failed");
        }
    }

    @Nested
    @DisplayName("getCause()")
    class GetCause {

        @Test
        @DisplayName("returns the cause passed to constructor")
        void returnsCausePassedToConstructor() {
            var cause = new SQLException("data error");
            var exception = new UserCodeException(cause);

            assertThat(exception.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("returns same cause on multiple calls")
        void returnsSameCauseOnMultipleCalls() {
            var cause = new IOException("stable error");
            var exception = new UserCodeException(cause);

            assertThat(exception.getCause()).isSameAs(exception.getCause());
        }
    }

    @Nested
    @DisplayName("semantic exception handling")
    class SemanticExceptionHandling {

        @Test
        @DisplayName("AbortedException cause is preserved through wrap")
        void abortedExceptionCauseIsPreserved() {
            var cause = new AbortedException("setup failed");
            var runtimeException = new RuntimeException(cause);

            var result = UserCodeException.wrap(runtimeException);

            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isInstanceOf(AbortedException.class);
            assertThat(result.getCause().getMessage()).isEqualTo("setup failed");
        }

        @Test
        @DisplayName("SkipException cause is preserved through wrap")
        void skipExceptionCauseIsPreserved() {
            var cause = new SkipException("prerequisite not met");
            var runtimeException = new RuntimeException(cause);

            var result = UserCodeException.wrap(runtimeException);

            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isInstanceOf(SkipException.class);
            assertThat(result.getCause().getMessage()).isEqualTo("prerequisite not met");
        }

        @Test
        @DisplayName("FailException cause is preserved through wrap")
        void failExceptionCauseIsPreserved() {
            var cause = new FailException("assertion failed");
            var runtimeException = new RuntimeException(cause);

            var result = UserCodeException.wrap(runtimeException);

            assertThat(result).isInstanceOf(UserCodeException.class);
            assertThat(result.getCause()).isInstanceOf(FailException.class);
            assertThat(result.getCause().getMessage()).isEqualTo("assertion failed");
        }
    }

    private static class CustomCheckedException extends Exception {
        CustomCheckedException(final String message) {
            super(message);
        }
    }
}
