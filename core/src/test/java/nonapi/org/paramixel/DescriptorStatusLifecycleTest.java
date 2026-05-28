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
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Metadata;
import org.paramixel.api.action.Mode;
import org.paramixel.api.action.Step;

@DisplayName("Descriptor status lifecycle")
class DescriptorStatusLifecycleTest {

    @Test
    @DisplayName("new descriptor starts PENDING")
    void newDescriptorStartsPending() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));

        assertThat(descriptor.metadata().status()).isEqualTo(Status.PENDING);
        assertThat(descriptor.metadata().isCompleted()).isFalse();
    }

    @Test
    @DisplayName("PENDING to RUNNING is valid")
    void pendingToRunningIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));

        descriptor.setStatus(Status.RUNNING);

        assertThat(descriptor.metadata().status()).isEqualTo(Status.RUNNING);
        assertThat(descriptor.metadata().isCompleted()).isFalse();
    }

    @Test
    @DisplayName("RUNNING to PASSED is valid")
    void runningToPassedIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);

        descriptor.setStatus(Status.PASSED);

        assertThat(descriptor.metadata().status()).isEqualTo(Status.PASSED);
        assertThat(descriptor.metadata().isCompleted()).isTrue();
    }

    @Test
    @DisplayName("RUNNING to FAILED is valid")
    void runningToFailedIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);

        descriptor.setStatus(Status.FAILED);

        assertThat(descriptor.metadata().status()).isEqualTo(Status.FAILED);
        assertThat(descriptor.metadata().isCompleted()).isTrue();
    }

    @Test
    @DisplayName("RUNNING to SKIPPED is valid")
    void runningToSkippedIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);

        descriptor.setStatus(Status.SKIPPED);

        assertThat(descriptor.metadata().status()).isEqualTo(Status.SKIPPED);
        assertThat(descriptor.metadata().isCompleted()).isTrue();
    }

    @Test
    @DisplayName("RUNNING to ABORTED is valid")
    void runningToAbortedIsValid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);

        descriptor.setStatus(Status.ABORTED);

        assertThat(descriptor.metadata().status()).isEqualTo(Status.ABORTED);
        assertThat(descriptor.metadata().isCompleted()).isTrue();
    }

    @Test
    @DisplayName("PENDING to terminal status is invalid")
    void pendingToTerminalIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));

        assertThatThrownBy(() -> descriptor.setStatus(Status.PASSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Metadata must transition from PENDING to RUNNING");
    }

    @Test
    @DisplayName("setting PENDING status is invalid")
    void settingPendingIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));

        assertThatThrownBy(() -> descriptor.setStatus(Status.PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set PENDING status");
    }

    @Test
    @DisplayName("setting null status is invalid")
    void settingNullStatusIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));

        assertThatThrownBy(() -> descriptor.setStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status is null");
    }

    @Test
    @DisplayName("RUNNING to RUNNING is invalid")
    void runningToRunningIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);

        assertThatThrownBy(() -> descriptor.setStatus(Status.RUNNING)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("terminal to any status is invalid")
    void terminalToAnyStatusIsInvalid() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.PASSED);

        assertThatThrownBy(() -> descriptor.setStatus(Status.RUNNING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");
    }

    @Test
    @DisplayName("setting terminal status with Status factory records message")
    void settingTerminalStatusWithStatusFactoryRecordsMessage() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);
        descriptor.setStatus(Status.failed("error message"));

        assertThat(descriptor.metadata().message()).contains("error message");
        assertThat(descriptor.metadata().throwable()).isEmpty();
    }

    @Test
    @DisplayName("setting terminal status with Status factory records throwable")
    void settingTerminalStatusWithStatusFactoryRecordsThrowable() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);
        var exception = new RuntimeException("boom");
        descriptor.setStatus(Status.failed("boom", exception));

        assertThat(descriptor.metadata().throwable()).containsSame(exception);
    }

    @Test
    @DisplayName("run duration is zero before execution")
    void runDurationIsZeroBeforeExecution() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));

        assertThat(descriptor.metadata().runDuration()).isZero();
    }

    @Test
    @DisplayName("run duration is measured after execution")
    void runDurationIsMeasuredAfterExecution() throws InterruptedException {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.setStatus(Status.RUNNING);
        Thread.sleep(10);
        descriptor.setStatus(Status.PASSED);

        assertThat(descriptor.metadata().runDuration()).isPositive();
    }

    @Test
    @DisplayName("descriptor has metadata")
    void descriptorHasMetadata() {
        Action<?> action = Step.of("test", obj -> {});
        var descriptor = new ConcreteDescriptor(action);

        Metadata metadata = descriptor.metadata();
        assertThat(metadata.name()).isEqualTo("test");
        assertThat(metadata.className()).contains("Step");
    }

    @Test
    @DisplayName("markScheduled rejects duplicate scheduling")
    void markScheduledRejectsDuplicateScheduling() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.markScheduled(Mode.RUN);

        assertThatThrownBy(() -> descriptor.markScheduled(Mode.RUN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already scheduled");
    }

    @Test
    @DisplayName("isScheduled returns false before scheduling")
    void isScheduledReturnsFalseBeforeScheduling() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));

        assertThat(descriptor.isScheduled()).isFalse();
    }

    @Test
    @DisplayName("isScheduled returns true after scheduling")
    void isScheduledReturnsTrueAfterScheduling() {
        var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
        descriptor.markScheduled(Mode.RUN);

        assertThat(descriptor.isScheduled()).isTrue();
    }
}
