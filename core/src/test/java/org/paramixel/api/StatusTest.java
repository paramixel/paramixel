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
        assertThat(Status.RUNNING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("PENDING is pending and not terminal")
    void pendingIsPending() {
        assertThat(Status.PENDING.isPending()).isTrue();
        assertThat(Status.PENDING.isTerminal()).isFalse();
        assertThat(Status.PENDING.isPassed()).isFalse();
        assertThat(Status.PENDING.isFailed()).isFalse();
        assertThat(Status.PENDING.isSkipped()).isFalse();
        assertThat(Status.PENDING.isAborted()).isFalse();
        assertThat(Status.PENDING.isRunning()).isFalse();
    }

    @Test
    @DisplayName("PASSED is passed and terminal")
    void passedIsPassed() {
        assertThat(Status.PASSED.isPassed()).isTrue();
        assertThat(Status.PASSED.isTerminal()).isTrue();
        assertThat(Status.PASSED.isPending()).isFalse();
        assertThat(Status.PASSED.isFailed()).isFalse();
        assertThat(Status.PASSED.isSkipped()).isFalse();
        assertThat(Status.PASSED.isAborted()).isFalse();
        assertThat(Status.PASSED.isRunning()).isFalse();
    }

    @Test
    @DisplayName("FAILED is failed and terminal")
    void failedIsFailed() {
        assertThat(Status.FAILED.isFailed()).isTrue();
        assertThat(Status.FAILED.isTerminal()).isTrue();
        assertThat(Status.FAILED.isPassed()).isFalse();
        assertThat(Status.FAILED.isPending()).isFalse();
        assertThat(Status.FAILED.isSkipped()).isFalse();
        assertThat(Status.FAILED.isAborted()).isFalse();
        assertThat(Status.FAILED.isRunning()).isFalse();
    }

    @Test
    @DisplayName("SKIPPED is skipped and terminal")
    void skippedIsSkipped() {
        assertThat(Status.SKIPPED.isSkipped()).isTrue();
        assertThat(Status.SKIPPED.isTerminal()).isTrue();
        assertThat(Status.SKIPPED.isPassed()).isFalse();
        assertThat(Status.SKIPPED.isPending()).isFalse();
        assertThat(Status.SKIPPED.isFailed()).isFalse();
        assertThat(Status.SKIPPED.isAborted()).isFalse();
        assertThat(Status.SKIPPED.isRunning()).isFalse();
    }

    @Test
    @DisplayName("ABORTED is aborted and terminal")
    void abortedIsAborted() {
        assertThat(Status.ABORTED.isAborted()).isTrue();
        assertThat(Status.ABORTED.isTerminal()).isTrue();
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
    @DisplayName("Status equals is based on name only")
    void statusEqualsAndHashCode() {
        var failedWithMessage = Status.failed("err");
        var failedWithOtherMessage = Status.failed("other");

        assertThat(failedWithMessage).isEqualTo(Status.FAILED);
        assertThat(failedWithOtherMessage).isEqualTo(Status.FAILED);
        assertThat(failedWithMessage).isEqualTo(failedWithOtherMessage);
        assertThat(failedWithMessage.hashCode()).isEqualTo(Status.FAILED.hashCode());
        assertThat(Status.PASSED).isNotEqualTo(Status.FAILED);
    }

    @Test
    @DisplayName("Status toString returns name")
    void statusToString() {
        assertThat(Status.PASSED.toString()).isEqualTo("PASSED");
        assertThat(Status.failed("err").toString()).isEqualTo("FAILED");
    }
}
