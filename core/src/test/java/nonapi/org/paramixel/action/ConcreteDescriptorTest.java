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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Status;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteDescriptor")
class ConcreteDescriptorTest {

    @Nested
    @DisplayName("structure")
    class Structure {

        @Test
        @DisplayName("a compiled descriptor exposes its final slots")
        void exposesFinalSlots() {
            var before = TestDescriptors.of(Step.step("before", context -> {}));
            var body = TestDescriptors.of(Step.step("body", context -> {}));
            var after = TestDescriptors.of(Step.step("after", context -> {}));

            var descriptor = TestDescriptors.tree(Step.step("descriptor", context -> {}), before, List.of(body), after);

            assertThat(descriptor.before()).containsSame(before);
            assertThat(descriptor.children()).containsExactly(body);
            assertThat(descriptor.after()).containsSame(after);
            assertThat(before.parent()).containsSame(descriptor);
            assertThat(body.parent()).containsSame(descriptor);
            assertThat(after.parent()).containsSame(descriptor);
        }
    }

    @Nested
    @DisplayName("setStatus state machine")
    class SetStatusStateMachine {

        @Test
        @DisplayName("rejects re-entering RUNNING from a RUNNING state")
        void rejectsReenteringRunningFromRunning() {
            var descriptor = new ConcreteDescriptor(Step.step("step", context -> {}));
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
            var child = TestDescriptors.of(Step.step("child", context -> {}));
            var root = TestDescriptors.tree(
                    Parallel.parallel("root", "t0")
                            .child(Step.step("x", context -> {}))
                            .build(),
                    List.of(child));

            root.abort(Status.ABORTED, new AssertionError());

            var childStatus = child.status();
            assertThat(childStatus.isAborted()).isTrue();
            assertThat(childStatus.message()).isPresent();
            assertThat(childStatus.message().orElseThrow()).doesNotContain("null");
        }

        /**
         * Builds a lifecycle-shaped descriptor: an optional before slot, one body child, and an after slot.
         *
         * <p>The Parallel action is only a container carrier here; the descriptor shape is what the abort rules
         * depend on.
         */
        private ConcreteDescriptor lifecycle(final boolean withBefore) {
            var before = withBefore ? TestDescriptors.of(Step.step("before", context -> {})) : null;
            var body = TestDescriptors.of(Step.step("body", context -> {}));
            var after = TestDescriptors.of(Step.step("after", context -> {}));
            return TestDescriptors.tree(
                    Parallel.parallel("lifecycle", "t1")
                            .child(Step.step("x", context -> {}))
                            .build(),
                    before,
                    List.of(body),
                    after);
        }

        private MutableDescriptor slot(final Descriptor descriptor) {
            return (MutableDescriptor) descriptor;
        }

        @Test
        @DisplayName("never-started lifecycle: before and after are aborted")
        void pendingLifecycleAbortsAfter() {
            var root = lifecycle(true);

            var started = root.abort(Status.failed("timed out"), new IllegalStateException("timed out"));

            assertThat(started).isFalse();
            assertThat(root.before().orElseThrow().status().isAborted()).isTrue();
            assertThat(root.children().get(0).status().isAborted()).isTrue();
            assertThat(root.after().orElseThrow().status().isAborted()).isTrue();
        }

        @Test
        @DisplayName("before running: before is aborted and after is preserved")
        void runningBeforePreservesAfter() {
            var root = lifecycle(true);
            root.tryStart();
            slot(root.before().orElseThrow()).tryStart();

            var started = root.abort(Status.failed("timed out"), new IllegalStateException("timed out"));

            assertThat(started).isTrue();
            assertThat(root.status().isFailed()).isTrue();
            assertThat(root.before().orElseThrow().status().isAborted()).isTrue();
            assertThat(root.children().get(0).status().isAborted()).isTrue();
            assertThat(slot(root.after().orElseThrow()).status().isPending()).isTrue();
        }

        @Test
        @DisplayName("before completed: after is preserved")
        void completedBeforePreservesAfter() {
            var root = lifecycle(true);
            root.tryStart();
            var before = slot(root.before().orElseThrow());
            before.tryStart();
            before.setStatus(Status.PASSED);

            root.abort(Status.failed("timed out"), new IllegalStateException("timed out"));

            assertThat(before.status().isPassed()).isTrue();
            assertThat(slot(root.after().orElseThrow()).status().isPending()).isTrue();
        }

        @Test
        @DisplayName("lifecycle started but before not yet attempted: after is aborted")
        void startedLifecycleWithUnattemptedBeforeAbortsAfter() {
            var root = lifecycle(true);
            root.tryStart();

            root.abort(Status.failed("timed out"), new IllegalStateException("timed out"));

            assertThat(root.before().orElseThrow().status().isAborted()).isTrue();
            assertThat(root.after().orElseThrow().status().isAborted()).isTrue();
        }

        @Test
        @DisplayName("no before: after is preserved only once the lifecycle has started")
        void lifecycleWithoutBeforeUsesOwnStart() {
            var pending = lifecycle(false);
            pending.abort(Status.failed("timed out"), new IllegalStateException("timed out"));
            assertThat(pending.after().orElseThrow().status().isAborted()).isTrue();

            var running = lifecycle(false);
            running.tryStart();
            running.abort(Status.failed("timed out"), new IllegalStateException("timed out"));
            assertThat(slot(running.after().orElseThrow()).status().isPending()).isTrue();
        }
    }

    @Nested
    @DisplayName("tryStart")
    class TryStart {

        @Test
        @DisplayName("starts a pending descriptor")
        void startsPending() {
            var descriptor = new ConcreteDescriptor(Step.step("step", context -> {}));

            assertThat(descriptor.tryStart()).isTrue();
            assertThat(descriptor.status().isRunning()).isTrue();
            assertThat(descriptor.tryStart()).as("already running").isTrue();
        }

        @Test
        @DisplayName("refuses a descriptor aborted before it started")
        void refusesAborted() {
            var descriptor = new ConcreteDescriptor(Step.step("step", context -> {}));
            descriptor.abort(Status.ABORTED, new IllegalStateException("cancelled"));

            assertThat(descriptor.tryStart()).isFalse();
            assertThat(descriptor.status().isAborted()).isTrue();
        }
    }

    @Nested
    @DisplayName("read-only accessors")
    class ReadOnlyAccessors {

        @Test
        @DisplayName("displayName is the bound action display name")
        void displayName() {
            var descriptor = new ConcreteDescriptor(null, Step.step("step", context -> {}));

            assertThat(descriptor.displayName()).isEqualTo("step");
            assertThat(descriptor.displayName()).isEqualTo(descriptor.action().displayName());
        }

        @Test
        @DisplayName("path joins the display names of the ancestors, excluding the run root")
        void path() {
            var root = new ConcreteDescriptor(
                    null,
                    Scope.scope(nonapi.org.paramixel.listener.Constants.ROOT_NAME)
                            .body(Step.step("body", c -> {}))
                            .build());
            var middle = new ConcreteDescriptor(
                    root,
                    Sequential.sequential("suite")
                            .child(Step.step("x", c -> {}))
                            .build());
            var leaf = new ConcreteDescriptor(middle, Step.step("leaves", context -> {}));

            assertThat(root.path()).isEmpty();
            assertThat(middle.path()).isEqualTo("suite");
            assertThat(leaf.path()).isEqualTo("suite / leaves");
            assertThat(leaf.path()).isEqualTo(leaf.path());
        }

        @Test
        @DisplayName("duration is empty until the descriptor both starts and completes")
        void duration() {
            var descriptor = new ConcreteDescriptor(null, Step.step("step", context -> {}));
            assertThat(descriptor.duration()).isEmpty();

            descriptor.setStatus(Status.RUNNING);
            descriptor.setStatus(Status.PASSED);
            assertThat(descriptor.duration()).isPresent();
            assertThat(descriptor.duration().orElseThrow()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
        }
    }
}
