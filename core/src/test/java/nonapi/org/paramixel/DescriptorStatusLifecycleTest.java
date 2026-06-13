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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("Descriptor status lifecycle")
class DescriptorStatusLifecycleTest {

    @Test
    @DisplayName("new descriptor starts PENDING")
    void newDescriptorStartsPending() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));

        assertThat(descriptor.status()).isEqualTo(Status.PENDING);
        assertThat(descriptor.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("PENDING to RUNNING is valid")
    void pendingToRunningIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));

        descriptor.setStatus(Status.RUNNING);

        assertThat(descriptor.status()).isEqualTo(Status.RUNNING);
        assertThat(descriptor.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("RUNNING to PASSED is valid")
    void runningToPassedIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);

        descriptor.setStatus(Status.PASSED);

        assertThat(descriptor.isPassed()).isTrue();
        assertThat(descriptor.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("RUNNING to FAILED is valid")
    void runningToFailedIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);

        descriptor.setStatus(Status.FAILED);

        assertThat(descriptor.isFailed()).isTrue();
        assertThat(descriptor.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("RUNNING to SKIPPED is valid")
    void runningToSkippedIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);

        descriptor.setStatus(Status.SKIPPED);

        assertThat(descriptor.isSkipped()).isTrue();
        assertThat(descriptor.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("RUNNING to ABORTED is valid")
    void runningToAbortedIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);

        descriptor.setStatus(Status.ABORTED);

        assertThat(descriptor.isAborted()).isTrue();
        assertThat(descriptor.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("PENDING to terminal status is invalid")
    void pendingToTerminalIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));

        assertThatThrownBy(() -> descriptor.setStatus(Status.PASSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Descriptor must transition from PENDING to RUNNING");
    }

    @Test
    @DisplayName("setting PENDING status is invalid")
    void settingPendingIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));

        assertThatThrownBy(() -> descriptor.setStatus(Status.PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set PENDING status");
    }

    @Test
    @DisplayName("setting null status is invalid")
    void settingNullStatusIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));

        assertThatThrownBy(() -> descriptor.setStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status is null");
    }

    @Test
    @DisplayName("RUNNING to RUNNING is invalid")
    void runningToRunningIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);

        assertThatThrownBy(() -> descriptor.setStatus(Status.RUNNING)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("terminal to any status is a no-op")
    void terminalToAnyStatusIsNoOp() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);

        // Calling setStatus on an already-terminal descriptor should silently no-op
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.FAILED);
        descriptor.setStatus(Status.SKIPPED);

        // Descriptor retains original terminal state
        assertThat(descriptor.isPassed()).isTrue();
        assertThat(descriptor.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("setting terminal status with Status factory records message")
    void settingTerminalStatusWithStatusFactoryRecordsMessage() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.failed("error message"));

        assertThat(descriptor.message()).contains("error message");
        assertThat(descriptor.throwable()).isEmpty();
    }

    @Test
    @DisplayName("setting terminal status with Status factory records throwable")
    void settingTerminalStatusWithStatusFactoryRecordsThrowable() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);
        var exception = new RuntimeException("boom");
        descriptor.setStatus(Status.failed("boom", exception));

        assertThat(descriptor.throwable()).containsSame(exception);
    }

    @Test
    @DisplayName("timestamps are empty before execution")
    void timestampsAreEmptyBeforeExecution() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));

        assertThat(descriptor.startedAt()).isEmpty();
        assertThat(descriptor.completedAt()).isEmpty();
    }

    @Test
    @DisplayName("timestamps are recorded during execution")
    void timestampsAreRecordedDuringExecution() throws InterruptedException {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);
        assertThat(descriptor.startedAt()).isPresent();
        assertThat(descriptor.completedAt()).isEmpty();
        Thread.sleep(10);
        descriptor.setStatus(Status.PASSED);

        assertThat(descriptor.completedAt()).isPresent();
        assertThat(descriptor.completedAt().orElseThrow())
                .isAfterOrEqualTo(descriptor.startedAt().orElseThrow());
    }

    @Test
    @DisplayName("descriptor exposes action")
    void descriptorExposesAction() {
        var action = Step.of("test", context -> {});
        var descriptor = new ConcreteDescriptor(action);

        assertThat(descriptor.action()).isSameAs(action);
        assertThat(descriptor.action().displayName()).isEqualTo("test");
        assertThat(descriptor.action().getClass().getName()).contains("Step");
    }

    @Test
    @DisplayName("markScheduled rejects duplicate scheduling")
    void markScheduledRejectsDuplicateScheduling() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.markScheduled();

        assertThatThrownBy(() -> descriptor.markScheduled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already scheduled");
    }

    @Test
    @DisplayName("isScheduled returns false before scheduling")
    void isScheduledReturnsFalseBeforeScheduling() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));

        assertThat(descriptor.isScheduled()).isFalse();
    }

    @Test
    @DisplayName("isScheduled returns true after scheduling")
    void isScheduledReturnsTrueAfterScheduling() {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.markScheduled();

        assertThat(descriptor.isScheduled()).isTrue();
    }

    @Test
    @DisplayName("defensive setStatus on completed descriptor is a no-op")
    void defensiveSetStatusOnCompletedDescriptorIsNoOp() {
        // Simulates ConcreteRunner.runInternal() catch block:
        //   - Descriptor completed normally (PASSED)
        //   - A listener throws after completion
        //   - Catch block defensively calls setStatus(FAILED)
        var root = new ConcreteDescriptor(Step.of("test", ctx -> {}));
        root.setStatus(Status.RUNNING);
        root.setStatus(Status.PASSED);

        // This must not throw
        root.setStatus(Status.failed("listener error", new RuntimeException("original listener exception")));

        // Descriptor retains its original terminal state (PASSED — not changed to FAILED)
        assertThat(root.isPassed()).isTrue();
        assertThat(root.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("no-op on terminal descriptor does not change timestamps")
    void noOpOnTerminalDescriptorDoesNotChangeTimestamps() throws InterruptedException {
        var descriptor = new ConcreteDescriptor(Step.of("test", context -> {}));
        descriptor.setStatus(Status.RUNNING);
        Thread.sleep(10);
        descriptor.setStatus(Status.PASSED);

        var startedAtBefore = descriptor.startedAt().orElseThrow();
        var completedAtBefore = descriptor.completedAt().orElseThrow();

        // No-op call
        descriptor.setStatus(Status.FAILED);

        // Timestamps must be unchanged
        assertThat(descriptor.startedAt()).containsSame(startedAtBefore);
        assertThat(descriptor.completedAt()).containsSame(completedAtBefore);
    }

    @Test
    @DisplayName("no-op on all terminal states (PASSED, FAILED, SKIPPED, ABORTED)")
    void noOpOnAllTerminalStates() {
        // PASSED → no-op on setStatus(FAILED)
        var passed = new ConcreteDescriptor(Step.of("test", ctx -> {}));
        passed.setStatus(Status.RUNNING);
        passed.setStatus(Status.PASSED);
        passed.setStatus(Status.FAILED);
        assertThat(passed.isPassed()).isTrue();

        // FAILED → no-op on setStatus(PASSED)
        var failed = new ConcreteDescriptor(Step.of("test", ctx -> {}));
        failed.setStatus(Status.RUNNING);
        failed.setStatus(Status.FAILED);
        failed.setStatus(Status.PASSED);
        assertThat(failed.isFailed()).isTrue();

        // SKIPPED → no-op on setStatus(ABORTED)
        var skipped = new ConcreteDescriptor(Step.of("test", ctx -> {}));
        skipped.setStatus(Status.RUNNING);
        skipped.setStatus(Status.SKIPPED);
        skipped.setStatus(Status.ABORTED);
        assertThat(skipped.isSkipped()).isTrue();

        // ABORTED → no-op on setStatus(SKIPPED)
        var aborted = new ConcreteDescriptor(Step.of("test", ctx -> {}));
        aborted.setStatus(Status.RUNNING);
        aborted.setStatus(Status.ABORTED);
        aborted.setStatus(Status.SKIPPED);
        assertThat(aborted.isAborted()).isTrue();
    }
}
