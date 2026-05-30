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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nonapi.org.paramixel.action.ConcreteDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Status;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteMetadata setStatus transitions")
class ConcreteMetadataSetStatusTest {

    @Nested
    @DisplayName("PASSED to FAILED (terminal to terminal - invalid)")
    class PassedToFailed {

        @Test
        @DisplayName("PASSED to FAILED is invalid")
        void passedToFailedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.PASSED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.FAILED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("PASSED to ABORTED (terminal to terminal - invalid)")
    class PassedToAborted {

        @Test
        @DisplayName("PASSED to ABORTED is invalid")
        void passedToAbortedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.PASSED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.ABORTED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("PASSED to SKIPPED (terminal to terminal - invalid)")
    class PassedToSkipped {

        @Test
        @DisplayName("PASSED to SKIPPED is invalid")
        void passedToSkippedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.PASSED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.SKIPPED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("PASSED to RUNNING (terminal to non-terminal - invalid)")
    class PassedToRunning {

        @Test
        @DisplayName("PASSED to RUNNING is invalid")
        void passedToRunningIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.PASSED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.RUNNING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("PASSED to PENDING (terminal to PENDING - invalid)")
    class PassedToPending {

        @Test
        @DisplayName("PASSED to PENDING is invalid")
        void passedToPendingIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.PASSED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.PENDING))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cannot set PENDING status");
        }
    }

    @Nested
    @DisplayName("PASSED to PASSED (terminal to same terminal - invalid)")
    class PassedToPassed {

        @Test
        @DisplayName("PASSED to PASSED is invalid")
        void passedToPassedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.PASSED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.PASSED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("FAILED to any status (terminal - invalid)")
    class FailedToAny {

        @Test
        @DisplayName("FAILED to PASSED is invalid")
        void failedToPassedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.FAILED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.PASSED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }

        @Test
        @DisplayName("FAILED to RUNNING is invalid")
        void failedToRunningIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.FAILED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.RUNNING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("SKIPPED to any status (terminal - invalid)")
    class SkippedToAny {

        @Test
        @DisplayName("SKIPPED to PASSED is invalid")
        void skippedToPassedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.SKIPPED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.PASSED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }

        @Test
        @DisplayName("SKIPPED to FAILED is invalid")
        void skippedToFailedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.SKIPPED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.FAILED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("ABORTED to any status (terminal - invalid)")
    class AbortedToAny {

        @Test
        @DisplayName("ABORTED to PASSED is invalid")
        void abortedToPassedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.ABORTED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.PASSED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }

        @Test
        @DisplayName("ABORTED to FAILED is invalid")
        void abortedToFailedIsInvalid() {
            var descriptor = new ConcreteDescriptor(Step.of("test", obj -> {}));
            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.ABORTED);

            assertThatThrownBy(() -> descriptor.setStatus(Status.FAILED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }
}
