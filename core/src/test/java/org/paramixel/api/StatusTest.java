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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nonapi.org.paramixel.exception.UserCodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Status")
class StatusTest {

    @Test
    @DisplayName("RUNNING is running")
    void runningIsRunning() {
        assertThat(Status.RUNNING.isRunning()).isTrue();
        assertThat(Status.RUNNING.isPending()).isFalse();
        assertThat(Status.RUNNING.isPassed()).isFalse();
        assertThat(Status.RUNNING.isFailed()).isFalse();
        assertThat(Status.RUNNING.isSkipped()).isFalse();
        assertThat(Status.RUNNING.isAborted()).isFalse();
    }

    @Test
    @DisplayName("PENDING is pending")
    void pendingIsPending() {
        assertThat(Status.PENDING.isPending()).isTrue();
        assertThat(Status.PENDING.isPassed()).isFalse();
        assertThat(Status.PENDING.isFailed()).isFalse();
        assertThat(Status.PENDING.isSkipped()).isFalse();
        assertThat(Status.PENDING.isAborted()).isFalse();
        assertThat(Status.PENDING.isRunning()).isFalse();
    }

    @Test
    @DisplayName("PASSED is passed")
    void passedIsPassed() {
        assertThat(Status.PASSED.isPassed()).isTrue();
        assertThat(Status.PASSED.isPending()).isFalse();
        assertThat(Status.PASSED.isFailed()).isFalse();
        assertThat(Status.PASSED.isSkipped()).isFalse();
        assertThat(Status.PASSED.isAborted()).isFalse();
        assertThat(Status.PASSED.isRunning()).isFalse();
    }

    @Test
    @DisplayName("FAILED is failed")
    void failedIsFailed() {
        assertThat(Status.FAILED.isFailed()).isTrue();
        assertThat(Status.FAILED.isPassed()).isFalse();
        assertThat(Status.FAILED.isPending()).isFalse();
        assertThat(Status.FAILED.isSkipped()).isFalse();
        assertThat(Status.FAILED.isAborted()).isFalse();
        assertThat(Status.FAILED.isRunning()).isFalse();
    }

    @Test
    @DisplayName("SKIPPED is skipped")
    void skippedIsSkipped() {
        assertThat(Status.SKIPPED.isSkipped()).isTrue();
        assertThat(Status.SKIPPED.isPassed()).isFalse();
        assertThat(Status.SKIPPED.isPending()).isFalse();
        assertThat(Status.SKIPPED.isFailed()).isFalse();
        assertThat(Status.SKIPPED.isAborted()).isFalse();
        assertThat(Status.SKIPPED.isRunning()).isFalse();
    }

    @Test
    @DisplayName("ABORTED is aborted")
    void abortedIsAborted() {
        assertThat(Status.ABORTED.isAborted()).isTrue();
        assertThat(Status.ABORTED.isPassed()).isFalse();
        assertThat(Status.ABORTED.isPending()).isFalse();
        assertThat(Status.ABORTED.isFailed()).isFalse();
        assertThat(Status.ABORTED.isSkipped()).isFalse();
        assertThat(Status.ABORTED.isRunning()).isFalse();
    }

    @Test
    @DisplayName("status names match constants")
    void statusNames() {
        assertThat(Status.PENDING.name()).isEqualTo("PENDING");
        assertThat(Status.RUNNING.name()).isEqualTo("RUNNING");
        assertThat(Status.PASSED.name()).isEqualTo("PASSED");
        assertThat(Status.FAILED.name()).isEqualTo("FAILED");
        assertThat(Status.SKIPPED.name()).isEqualTo("SKIPPED");
        assertThat(Status.ABORTED.name()).isEqualTo("ABORTED");
    }

    @Test
    @DisplayName("canonical statuses have no message or throwable")
    void canonicalStatusesHaveNoMessageOrThrowable() {
        for (Status status : new Status[] {
            Status.PENDING, Status.RUNNING, Status.PASSED, Status.FAILED, Status.SKIPPED, Status.ABORTED
        }) {
            assertThat(status.message()).isEmpty();
            assertThat(status.throwable()).isEmpty();
        }
    }

    @Test
    @DisplayName("Status with message carries message and no throwable")
    void statusWithMessage() {
        var status = Status.failed("timeout");

        assertThat(status.isFailed()).isTrue();
        assertThat(status.name()).isEqualTo("FAILED");
        assertThat(status.message()).contains("timeout");
        assertThat(status.throwable()).isEmpty();
    }

    @Test
    @DisplayName("Status with message and throwable carries both")
    void statusWithMessageAndThrowable() {
        var exception = new RuntimeException("boom");
        var status = Status.failed("boom", exception);

        assertThat(status.isFailed()).isTrue();
        assertThat(status.name()).isEqualTo("FAILED");
        assertThat(status.throwable()).containsSame(exception);
        assertThat(status.message()).contains("boom");
    }

    @Test
    @DisplayName("Status with null-message throwable returns explicit message")
    void statusWithNullMessageThrowable() {
        var exception = new RuntimeException();
        var status = Status.failed("failed", exception);

        assertThat(status.throwable()).containsSame(exception);
        assertThat(status.message()).contains("failed");
    }

    @Test
    @DisplayName("Status equals and hashCode include message and throwable")
    void statusEqualsAndHashCode() {
        var failedWithMessage = Status.failed("err");
        var failedWithOtherMessage = Status.failed("other");

        assertThat(failedWithMessage).isNotEqualTo(Status.FAILED);
        assertThat(failedWithMessage).isNotEqualTo(failedWithOtherMessage);
        assertThat(failedWithMessage.hashCode()).isNotEqualTo(Status.FAILED.hashCode());
        assertThat(Status.PASSED).isNotEqualTo(Status.FAILED);

        var failedWithSameMessage = Status.failed("err");
        assertThat(failedWithMessage).isEqualTo(failedWithSameMessage);
        assertThat(failedWithMessage.hashCode()).isEqualTo(failedWithSameMessage.hashCode());

        var exception1 = new RuntimeException("boom");
        var exception2 = new RuntimeException("bang");
        var exception3 = new RuntimeException("boom");
        var failedWithThrowable1 = Status.failed("err", exception1);
        var failedWithThrowable2 = Status.failed("err", exception2);
        var failedWithThrowable3 = Status.failed("err", exception3);

        assertThat(failedWithThrowable1).isNotEqualTo(failedWithThrowable2);
        assertThat(failedWithThrowable1).isNotEqualTo(failedWithMessage);
        assertThat(failedWithThrowable1).isEqualTo(Status.failed("err", exception1));
        assertThat(failedWithThrowable1).isNotEqualTo(failedWithThrowable3);
        assertThat(failedWithThrowable1).isNotEqualTo(Status.failed("err", new IllegalStateException("boom")));
    }

    @Test
    @DisplayName("Status toString returns name")
    void statusToString() {
        assertThat(Status.PASSED.toString()).isEqualTo("PASSED");
        assertThat(Status.failed("err").toString()).isEqualTo("FAILED");
    }

    private static final class EquatableThrowable extends Throwable {
        EquatableThrowable(String message) {
            super(message);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EquatableThrowable other)) return false;
            return Objects.equals(getMessage(), other.getMessage());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getMessage());
        }
    }

    @Test
    @DisplayName("equals respects Throwable subclass equality")
    void equalsWithEqualThrowableInstances() {
        var s1 = Status.failed("boom", new EquatableThrowable("same"));
        var s2 = Status.failed("boom", new EquatableThrowable("same"));

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    @DisplayName("equals respects Throwable subclass inequality")
    void equalsWithDifferentThrowableInstances() {
        var s1 = Status.failed("boom", new EquatableThrowable("one"));
        var s2 = Status.failed("boom", new EquatableThrowable("two"));

        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    @DisplayName("hashCode is consistent with Throwable subclass equality")
    void hashCodeConsistencyWithEqualThrowables() {
        var s1 = Status.failed("boom", new EquatableThrowable("same"));
        var s2 = Status.failed("boom", new EquatableThrowable("same"));

        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    @DisplayName("HashSet deduplicates Status with equal Throwable instances")
    void hashSetBehavior() {
        var s1 = Status.failed("boom", new EquatableThrowable("same"));
        var s2 = Status.failed("boom", new EquatableThrowable("same"));

        var set = new HashSet<Status>();
        set.add(s1);
        set.add(s2);

        assertThat(set).hasSize(1);
    }

    @Test
    @DisplayName("HashMap keys deduplicate Status with equal Throwable instances")
    void hashMapKeyBehavior() {
        var s1 = Status.failed("boom", new EquatableThrowable("same"));
        var s2 = Status.failed("boom", new EquatableThrowable("same"));

        var map = new HashMap<Status, String>();
        map.put(s1, "first");
        map.put(s2, "second");

        assertThat(map).hasSize(1);
        assertThat(map.get(s2)).isEqualTo("second");
    }

    @Nested
    @DisplayName("fromThrowable()")
    class FromThrowable {

        @Test
        @DisplayName("FailException wrapped in UserCodeException returns failed with message and throwable")
        void failExceptionWrappedInUserCodeException() {
            var failException = new FailException("assertion");
            var status = Status.fromThrowable(new UserCodeException(failException));

            assertThat(status.isFailed()).isTrue();
            assertThat(status.message()).contains("assertion");
            assertThat(status.throwable()).containsSame(failException);
        }

        @Test
        @DisplayName("SkipException wrapped in UserCodeException returns skipped with message and throwable")
        void skipExceptionWrappedInUserCodeException() {
            var skipException = new SkipException("not ready");
            var status = Status.fromThrowable(new UserCodeException(skipException));

            assertThat(status.isSkipped()).isTrue();
            assertThat(status.message()).contains("not ready");
            assertThat(status.throwable()).containsSame(skipException);
        }

        @Test
        @DisplayName("AbortedException wrapped in UserCodeException returns aborted with message and throwable")
        void abortedExceptionWrappedInUserCodeException() {
            var abortedException = new AbortedException("precondition");
            var status = Status.fromThrowable(new UserCodeException(abortedException));

            assertThat(status.isAborted()).isTrue();
            assertThat(status.message()).contains("precondition");
            assertThat(status.throwable()).containsSame(abortedException);
        }

        @Test
        @DisplayName("RuntimeException wrapping FailException without UserCodeException is treated as failure")
        void runtimeExceptionWrappingFailExceptionWithoutUserCodeExceptionIsTreatedAsFailure() {
            var status = Status.fromThrowable(new RuntimeException(new FailException("assertion")));

            assertThat(status.isFailed()).isTrue();
            assertThat(status.throwable()).containsInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("UserCodeException unwraps checked exception and preserves cause")
        void userCodeExceptionUnwrapsCheckedExceptionAndPreservesCause() {
            var cause = new IOException("disk offline");
            var status = Status.fromThrowable(new UserCodeException(cause));

            assertThat(status.isFailed()).isTrue();
            assertThat(status.message()).contains("disk offline");
            assertThat(status.throwable()).containsSame(cause);
        }

        @Test
        @DisplayName("plain checked exception is treated as failure with same throwable")
        void plainCheckedExceptionIsTreatedAsFailureWithSameThrowable() {
            var cause = new IOException("io failure");
            var status = Status.fromThrowable(cause);

            assertThat(status.isFailed()).isTrue();
            assertThat(status.message()).contains("io failure");
            assertThat(status.throwable()).containsSame(cause);
        }

        @Test
        @DisplayName("RuntimeException without UserCodeException is not unwrapped")
        void runtimeExceptionWithoutUserCodeExceptionIsNotUnwrapped() {
            var cause = new IOException("inner");
            var runtimeException = new RuntimeException("outer", cause);
            var status = Status.fromThrowable(runtimeException);

            assertThat(status.isFailed()).isTrue();
            assertThat(status.message()).contains("outer");
            assertThat(status.throwable()).containsSame(runtimeException);
        }

        @Test
        @DisplayName("UserCodeException wrapping InterruptedException restores interrupt flag")
        void userCodeExceptionWrappingInterruptedExceptionRestoresInterruptFlag() {
            Thread.interrupted();
            try {
                var status = Status.fromThrowable(new UserCodeException(new InterruptedException("interrupted")));

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

            var status = Status.fromThrowable(wrapper);

            assertThat(status.isFailed()).isTrue();
            assertThat(status.throwable()).containsSame(wrapper);
            assertThat(status.message()).contains("original");
        }
    }

    @Nested
    @DisplayName("aggregate()")
    class Aggregate {

        @Test
        @DisplayName("empty list returns PASSED")
        void emptyReturnsPassed() {
            assertThat(Status.aggregate(List.of())).isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("all PASSED returns PASSED")
        void allPassedReturnsPassed() {
            assertThat(Status.aggregate(descriptorsWith(Status.PASSED, Status.PASSED)))
                    .isSameAs(Status.PASSED);
        }

        @Test
        @DisplayName("ABORTED before FAILED returns FAILED")
        void abortedBeforeFailedReturnsFailed() {
            assertThat(Status.aggregate(descriptorsWith(Status.ABORTED, Status.FAILED)))
                    .isSameAs(Status.FAILED);
        }

        @Test
        @DisplayName("SKIPPED, ABORTED, FAILED returns FAILED")
        void skippedAbortedFailedReturnsFailed() {
            assertThat(Status.aggregate(descriptorsWith(Status.SKIPPED, Status.ABORTED, Status.FAILED)))
                    .isSameAs(Status.FAILED);
        }

        @Test
        @DisplayName("PASSED, ABORTED, PASSED returns ABORTED")
        void passedAbortedPassedReturnsAborted() {
            assertThat(Status.aggregate(descriptorsWith(Status.PASSED, Status.ABORTED, Status.PASSED)))
                    .isSameAs(Status.ABORTED);
        }

        @Test
        @DisplayName("SKIPPED, ABORTED returns ABORTED")
        void skippedAbortedReturnsAborted() {
            assertThat(Status.aggregate(descriptorsWith(Status.SKIPPED, Status.ABORTED)))
                    .isSameAs(Status.ABORTED);
        }

        @Test
        @DisplayName("non-terminal before terminal returns RUNNING")
        void nonTerminalReturnsRunning() {
            assertThat(Status.aggregate(descriptorsWith(Status.PENDING, Status.PASSED)))
                    .isSameAs(Status.RUNNING);
        }

        @Test
        @DisplayName("SKIPPED without FAILED or ABORTED returns SKIPPED")
        void skippedWithoutFailedOrAbortedReturnsSkipped() {
            assertThat(Status.aggregate(descriptorsWith(Status.SKIPPED, Status.PASSED)))
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
                private final Action action = Step.of("stub", ignored -> {});

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
                public boolean isPassed() {
                    return status.isPassed();
                }

                @Override
                public boolean isFailed() {
                    return status.isFailed();
                }

                @Override
                public boolean isSkipped() {
                    return status.isSkipped();
                }

                @Override
                public boolean isAborted() {
                    return status.isAborted();
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
                public Optional<String> message() {
                    return Optional.empty();
                }

                @Override
                public Optional<Throwable> throwable() {
                    return Optional.empty();
                }

                @Override
                public boolean isCompleted() {
                    return !status.isPending() && !status.isRunning();
                }

                @Override
                public List<Descriptor> children() {
                    return List.of();
                }
            };
        }
    }
}
