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

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteDescriptor")
class ConcreteDescriptorTest {

    @Nested
    @DisplayName("addChild validation")
    class AddChildValidation {

        @Test
        @DisplayName("rejects adding a descriptor as its own child")
        void rejectsAddingDescriptorAsOwnChild() {
            var descriptor = new ConcreteDescriptor(Step.of("self", context -> {}));

            assertThatThrownBy(() -> descriptor.addChild(descriptor))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("child is this descriptor");
        }
    }

    @Nested
    @DisplayName("setStatus state machine")
    class SetStatusStateMachine {

        @Test
        @DisplayName("rejects re-entering RUNNING from a RUNNING state")
        void rejectsReenteringRunningFromRunning() {
            var descriptor = new ConcreteDescriptor(Step.of("step", context -> {}));
            descriptor.setStatus(Status.RUNNING);

            assertThatThrownBy(() -> descriptor.setStatus(Status.RUNNING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Descriptor must transition from RUNNING to terminal status");
        }
    }

    @Nested
    @DisplayName("abort")
    class Abort {

        @Test
        @DisplayName("handles cause with null message")
        void handlesNullCauseMessage() {
            var root = new ConcreteDescriptor(Parallel.builder("root").build());
            var child = new ConcreteDescriptor(Step.of("child", context -> {}));
            root.addChild(child);
            root.freeze();

            root.abort(Status.ABORTED, new AssertionError());

            var childStatus = child.status();
            assertThat(childStatus.isAborted()).isTrue();
            assertThat(childStatus.message()).isPresent();
            assertThat(childStatus.message().orElseThrow()).doesNotContain("null");
        }
    }

    @Nested
    @DisplayName("scheduled future lifecycle")
    class ScheduledFutureLifecycle {

        @Test
        @DisplayName("completeFuture completes the scheduled future with the descriptor")
        void completeFutureCompletesScheduledFuture() {
            var descriptor = new ConcreteDescriptor(Step.of("step", context -> {}));
            CompletableFuture<Descriptor> future = descriptor.markScheduled();
            assertThat(future).isNotDone();

            descriptor.completeFuture();

            assertThat(future).isCompleted();
            assertThat(future.getNow(null)).isSameAs(descriptor);
        }

        @Test
        @DisplayName("completeFuture is a no-op when the scheduled future is already done")
        void completeFutureIsNoOpWhenAlreadyDone() {
            var descriptor = new ConcreteDescriptor(Step.of("step", context -> {}));
            CompletableFuture<Descriptor> future = descriptor.markScheduled();
            // Externally complete the future with a distinct sentinel so it is already
            // done when completeFuture runs.
            var sentinel = new ConcreteDescriptor(Step.of("sentinel", context -> {}));
            future.complete(sentinel);

            descriptor.completeFuture();

            // The original completion value is preserved; completeFuture did not override it.
            assertThat(future).isCompleted();
            assertThat(future.getNow(null)).isSameAs(sentinel);
        }

        @Test
        @DisplayName("completeFuture is a no-op when no scheduled future exists")
        void completeFutureIsNoOpWhenUnscheduled() {
            var descriptor = new ConcreteDescriptor(Step.of("step", context -> {}));

            // No exception and no observable state change for an unscheduled descriptor.
            descriptor.completeFuture();
            assertThat(descriptor.isScheduled()).isFalse();
        }
    }
}
