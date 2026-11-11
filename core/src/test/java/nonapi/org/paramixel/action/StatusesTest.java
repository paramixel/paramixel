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

package nonapi.org.paramixel.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import nonapi.org.paramixel.exception.UserCodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Statuses")
class StatusesTest {

    @Nested
    @DisplayName("fromThrowable()")
    class FromThrowable {

        @Test
        @DisplayName("FailException wrapped in UserCodeException returns failed with message and throwable")
        void failExceptionWrappedInUserCodeException() {
            var failException = new FailException("assertion");
            var status = Statuses.fromThrowable(new UserCodeException(failException));

            assertThat(status.isFailed()).isTrue();
            assertThat(status.message()).contains("assertion");
            assertThat(status.throwable()).containsSame(failException);
        }

        @Test
        @DisplayName("SkipException wrapped in UserCodeException returns skipped with message and throwable")
        void skipExceptionWrappedInUserCodeException() {
            var skipException = new SkipException("not ready");
            var status = Statuses.fromThrowable(new UserCodeException(skipException));

            assertThat(status.isSkipped()).isTrue();
            assertThat(status.message()).contains("not ready");
            assertThat(status.throwable()).containsSame(skipException);
        }

        @Test
        @DisplayName("AbortException wrapped in UserCodeException returns aborted with message and throwable")
        void abortedExceptionWrappedInUserCodeException() {
            var abortedException = new AbortException("precondition");
            var status = Statuses.fromThrowable(new UserCodeException(abortedException));

            assertThat(status.isAborted()).isTrue();
            assertThat(status.message()).contains("precondition");
            assertThat(status.throwable()).containsSame(abortedException);
        }

        @Test
        @DisplayName("RuntimeException wrapping FailException without UserCodeException is treated as failure")
        void runtimeExceptionWrappingFailExceptionWithoutUserCodeExceptionIsTreatedAsFailure() {
            var status = Statuses.fromThrowable(new RuntimeException(new FailException("assertion")));

            assertThat(status.isFailed()).isTrue();
            assertThat(status.throwable()).containsInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("UserCodeException unwraps checked exception and preserves cause")
        void userCodeExceptionUnwrapsCheckedExceptionAndPreservesCause() {
            var cause = new IOException("disk offline");
            var status = Statuses.fromThrowable(new UserCodeException(cause));

            assertThat(status.isFailed()).isTrue();
            assertThat(status.message()).contains("disk offline");
            assertThat(status.throwable()).containsSame(cause);
        }

        @Test
        @DisplayName("plain checked exception is treated as failure with same throwable")
        void plainCheckedExceptionIsTreatedAsFailureWithSameThrowable() {
            var cause = new IOException("io failure");
            var status = Statuses.fromThrowable(cause);

            assertThat(status.isFailed()).isTrue();
            assertThat(status.message()).contains("io failure");
            assertThat(status.throwable()).containsSame(cause);
        }

        @Test
        @DisplayName("RuntimeException without UserCodeException is not unwrapped")
        void runtimeExceptionWithoutUserCodeExceptionIsNotUnwrapped() {
            var cause = new IOException("inner");
            var runtimeException = new RuntimeException("outer", cause);
            var status = Statuses.fromThrowable(runtimeException);

            assertThat(status.isFailed()).isTrue();
            assertThat(status.message()).contains("outer");
            assertThat(status.throwable()).containsSame(runtimeException);
        }

        @Test
        @DisplayName("UserCodeException wrapping InterruptedException restores interrupt flag")
        void userCodeExceptionWrappingInterruptedExceptionRestoresInterruptFlag() {
            Thread.interrupted();
            try {
                var status = Statuses.fromThrowable(new UserCodeException(new InterruptedException("interrupted")));

                assertThat(status.isFailed()).isTrue();
                assertThat(status.throwable()).containsInstanceOf(InterruptedException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName("UserCodeException with null cause falls back to original wrapper")
        void userCodeExceptionWithNullCauseFallsBackToOriginalWrapper() throws Exception {
            var wrapper = new UserCodeException(new RuntimeException("original"));
            var causeField = UserCodeException.class.getDeclaredField("cause");
            causeField.setAccessible(true);
            causeField.set(wrapper, null);

            var status = Statuses.fromThrowable(wrapper);

            assertThat(status.isFailed()).isTrue();
            assertThat(status.throwable()).containsSame(wrapper);
            assertThat(status.message()).contains("original");
        }

        @Test
        @DisplayName("fromThrowable rejects null with descriptive NullPointerException")
        void fromThrowableRejectsNull() {
            assertThatThrownBy(() -> Statuses.fromThrowable(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("throwable is null");
        }
    }

    @Nested
    @DisplayName("aggregate()")
    class Aggregate {

        @Test
        @DisplayName("empty list returns PASSED")
        void emptyReturnsPassed() {
            assertThat(Statuses.aggregate(List.of())).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("all PASSED returns PASSED")
        void allPassedReturnsPassed() {
            assertThat(Statuses.aggregate(descriptorsWith(Status.PASSED, Status.PASSED)))
                    .isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("ABORTED before FAILED returns FAILED")
        void abortedBeforeFailedReturnsFailed() {
            assertThat(Statuses.aggregate(descriptorsWith(Status.ABORTED, Status.FAILED)))
                    .isSameAs(Status.FAILED);
        }

        @Test
        @DisplayName("SKIPPED, ABORTED, FAILED returns FAILED")
        void skippedAbortedFailedReturnsFailed() {
            assertThat(Statuses.aggregate(descriptorsWith(Status.SKIPPED, Status.ABORTED, Status.FAILED)))
                    .isSameAs(Status.FAILED);
        }

        @Test
        @DisplayName("PASSED, ABORTED, PASSED returns ABORTED")
        void passedAbortedPassedReturnsAborted() {
            assertThat(Statuses.aggregate(descriptorsWith(Status.PASSED, Status.ABORTED, Status.PASSED)))
                    .isSameAs(Status.ABORTED);
        }

        @Test
        @DisplayName("SKIPPED, ABORTED returns ABORTED")
        void skippedAbortedReturnsAborted() {
            assertThat(Statuses.aggregate(descriptorsWith(Status.SKIPPED, Status.ABORTED)))
                    .isSameAs(Status.ABORTED);
        }

        @Test
        @DisplayName("non-terminal before terminal returns RUNNING")
        void nonTerminalReturnsRunning() {
            assertThat(Statuses.aggregate(descriptorsWith(Status.PENDING, Status.PASSED)))
                    .isSameAs(Status.RUNNING);
        }

        @Test
        @DisplayName("SKIPPED without FAILED or ABORTED returns SKIPPED")
        void skippedWithoutFailedOrAbortedReturnsSkipped() {
            assertThat(Statuses.aggregate(descriptorsWith(Status.SKIPPED, Status.PASSED)))
                    .isSameAs(Status.SKIPPED);
        }

        private List<Descriptor> descriptorsWith(final Status... statuses) {
            var descriptors = new Descriptor[statuses.length];
            for (int i = 0; i < statuses.length; i++) {
                descriptors[i] = stubDescriptor(statuses[i]);
            }
            return List.of(descriptors);
        }

        private Descriptor stubDescriptor(final Status status) {
            return new Descriptor() {
                private final Action action = Step.step("stub", ignored -> {});

                @Override
                public Optional<Descriptor> parent() {
                    return Optional.empty();
                }

                @Override
                public Action action() {
                    return action;
                }

                @Override
                public String id() {
                    return "stub";
                }

                @Override
                public String path() {
                    return "stub";
                }

                @Override
                public Status status() {
                    return status;
                }

                @Override
                public Optional<Instant> startedAt() {
                    return Optional.empty();
                }

                @Override
                public Optional<Instant> completedAt() {
                    return Optional.empty();
                }

                @Override
                public List<Descriptor> children() {
                    return List.of();
                }
            };
        }
    }
}
