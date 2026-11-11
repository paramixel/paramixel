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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        assertThat(Status.PENDING.kind().name()).isEqualTo("PENDING");
        assertThat(Status.RUNNING.kind().name()).isEqualTo("RUNNING");
        assertThat(Status.PASSED.kind().name()).isEqualTo("PASSED");
        assertThat(Status.FAILED.kind().name()).isEqualTo("FAILED");
        assertThat(Status.SKIPPED.kind().name()).isEqualTo("SKIPPED");
        assertThat(Status.ABORTED.kind().name()).isEqualTo("ABORTED");
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
        assertThat(status.kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(status.message()).contains("timeout");
        assertThat(status.throwable()).isEmpty();
    }

    @Test
    @DisplayName("Status with message and throwable carries both")
    void statusWithMessageAndThrowable() {
        var exception = new RuntimeException("boom");
        var status = Status.failed("boom", exception);

        assertThat(status.isFailed()).isTrue();
        assertThat(status.kind()).isEqualTo(Status.Kind.FAILED);
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
    @DisplayName("Status equals and hashCode include the kind and message, not the throwable")
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

        var failedWithThrowable = Status.failed("err", new RuntimeException("boom"));

        assertThat(failedWithThrowable).isEqualTo(failedWithMessage);
        assertThat(failedWithThrowable).isEqualTo(Status.failed("err", new IllegalStateException("boom")));
        assertThat(failedWithThrowable).isNotEqualTo(Status.FAILED);
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
    @DisplayName("equals ignores the throwable")
    void equalsIgnoresThrowable() {
        var s1 = Status.failed("boom", new EquatableThrowable("one"));
        var s2 = Status.failed("boom", new EquatableThrowable("two"));

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
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

    @Test
    @DisplayName("kind identifies the outcome and supports switch")
    void kindIdentifiesOutcome() {
        assertThat(Status.failed("x").kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(Status.failed("x", new RuntimeException()).kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(Status.skipped("x").kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(Status.aborted("x").kind()).isEqualTo(Status.Kind.ABORTED);
        assertThat(Status.PASSED.kind()).isEqualTo(Status.Kind.PASSED);

        var description =
                switch (Status.failed("x").kind()) {
                    case PASSED -> "passed";
                    case FAILED -> "failed";
                    case SKIPPED -> "skipped";
                    case ABORTED -> "aborted";
                    case PENDING -> "pending";
                    case RUNNING -> "running";
                };
        assertThat(description).isEqualTo("failed");
    }

    @Test
    @DisplayName("Kind.isTerminal covers the four terminal kinds")
    void kindIsTerminal() {
        assertThat(Status.Kind.PENDING.isTerminal()).isFalse();
        assertThat(Status.Kind.RUNNING.isTerminal()).isFalse();
        assertThat(Status.Kind.PASSED.isTerminal()).isTrue();
        assertThat(Status.Kind.FAILED.isTerminal()).isTrue();
        assertThat(Status.Kind.SKIPPED.isTerminal()).isTrue();
        assertThat(Status.Kind.ABORTED.isTerminal()).isTrue();
    }
}
