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

import java.io.IOException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Throwables")
class ThrowablesTest {

    @Nested
    @DisplayName("unwrap")
    class Unwrap {

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            assertThat(Throwables.unwrap(null)).isNull();
        }

        @Test
        @DisplayName("returns input when not wrapped")
        void returnsInputWhenNotWrapped() {
            var ioException = new IOException("network unreachable");
            assertThat(Throwables.unwrap(ioException)).isSameAs(ioException);
        }

        @Test
        @DisplayName("recursively unwraps nested CompletionException")
        void recursivelyUnwrapsNestedCompletionException() {
            var cause = new IOException("disk error");
            var inner = new CompletionException(cause);
            var outer = new CompletionException(inner);
            assertThat(Throwables.unwrap(outer)).isSameAs(cause);
        }

        @Test
        @DisplayName("unwraps CompletionException wrapping Error")
        void unwrapsCompletionExceptionWrappingError() {
            var error = new OutOfMemoryError("heap exhausted");
            var completion = new CompletionException(error);
            assertThat(Throwables.unwrap(completion)).isSameAs(error);
        }

        @Test
        @DisplayName("unwraps CompletionException wrapping InterruptedException")
        void unwrapsCompletionExceptionWrappingInterrupted() {
            var interrupted = new InterruptedException("interrupted");
            var completion = new CompletionException(interrupted);
            assertThat(Throwables.unwrap(completion)).isSameAs(interrupted);
        }

        @Test
        @DisplayName("unwraps RuntimeException when cause is unrecoverable Error")
        void unwrapsRuntimeExceptionWhenCauseIsUnrecoverableError() {
            var error = new OutOfMemoryError("heap exhausted");
            var runtime = new RuntimeException(error);
            assertThat(Throwables.unwrap(runtime)).isSameAs(error);
        }

        @Test
        @DisplayName("unwraps RuntimeException when cause is Error")
        void unwrapsRuntimeExceptionWhenCauseIsError() {
            var error = new LinkageError("linkage error");
            var runtime = new RuntimeException(error);
            assertThat(Throwables.unwrap(runtime)).isSameAs(error);
        }

        @Test
        @DisplayName("unwraps RuntimeException when cause is InterruptedException")
        void unwrapsRuntimeExceptionWhenCauseIsInterrupted() {
            var interrupted = new InterruptedException("interrupted");
            var runtime = new RuntimeException(interrupted);
            assertThat(Throwables.unwrap(runtime)).isSameAs(interrupted);
        }

        @Test
        @DisplayName("does not unwrap RuntimeException when cause is ordinary exception")
        void doesNotUnwrapRuntimeExceptionWhenCauseIsOrdinary() {
            var cause = new IOException("file not found");
            var runtime = new RuntimeException(cause);
            assertThat(Throwables.unwrap(runtime)).isSameAs(runtime);
        }

        @Test
        @DisplayName("does not unwrap RuntimeException when cause is null")
        void doesNotUnwrapRuntimeExceptionWhenCauseIsNull() {
            var runtime = new RuntimeException();
            assertThat(Throwables.unwrap(runtime)).isSameAs(runtime);
        }
    }
}
