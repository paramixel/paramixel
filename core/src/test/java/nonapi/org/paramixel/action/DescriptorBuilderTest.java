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

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Instance;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Repeat;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Static;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;

@DisplayName("DescriptorBuilder")
class DescriptorBuilderTest {

    private final DescriptorBuilder builder = new DescriptorBuilder();

    @Test
    @DisplayName("discovers single leaf action")
    void discoversSingleLeafAction() {
        Action action = Step.of("leaf", context -> {});
        MutableDescriptor root = builder.discover(action);

        assertThat(root).isNotNull();
        assertThat(root.action()).isSameAs(action);
        assertThat(root.children()).isEmpty();
    }

    @Test
    @DisplayName("discovers composite action with children")
    void discoversCompositeWithChildren() {
        Action child1 = Step.of("child1", context -> {});
        Action child2 = Step.of("child2", context -> {});
        Action parent = Sequence.builder("parent").child(child1).child(child2).build();
        MutableDescriptor root = builder.discover(parent);

        assertThat(root.children()).hasSize(2);
        assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(child1);
        assertThat(((MutableDescriptor) root.children().get(1)).action()).isSameAs(child2);
    }

    @Test
    @DisplayName("child descriptors have parent reference")
    void childDescriptorsHaveParent() {
        Action child = Step.of("child", context -> {});
        Action parent = Sequence.builder("parent").child(child).build();
        MutableDescriptor root = builder.discover(parent);

        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).parent()).containsSame(root);
    }

    @Test
    @DisplayName("rejects null root action")
    void rejectsNullRootAction() {
        assertThatThrownBy(() -> builder.discover(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("root is null");
    }

    @Test
    @DisplayName("same action instance reused in multiple positions produces independent descriptors")
    void sameActionInstanceReusedProducesIndependentDescriptors() {
        Action shared = Step.of("shared", context -> {});
        Action parent = Sequence.builder("parent").child(shared).child(shared).build();
        MutableDescriptor root = builder.discover(parent);

        assertThat(root.children()).hasSize(2);
        assertThat(root.children().get(0)).isNotSameAs(root.children().get(1));
        assertThat(((MutableDescriptor) root.children().get(0)).action())
                .isSameAs(((MutableDescriptor) root.children().get(1)).action());
    }

    @Test
    @DisplayName("root descriptor starts with PENDING status")
    void rootDescriptorStartsPending() {
        Action action = Step.of("leaf", context -> {});
        MutableDescriptor root = builder.discover(action);

        assertThat(root.status().isPending()).isTrue();
    }

    @Test
    @DisplayName("discover leaves descriptor tree mutable until frozen")
    void discoverLeavesDescriptorTreeMutableUntilFrozen() {
        Action parent = Sequence.builder("parent")
                .child(Step.of("child", context -> {}))
                .build();
        MutableDescriptor root = builder.discover(parent);
        MutableDescriptor child = (MutableDescriptor) root.children().get(0);

        assertThat(root.isFrozen()).isFalse();
        assertThat(child.isFrozen()).isFalse();
    }

    @Test
    @DisplayName("freeze caches immutable children view")
    void freezeCachesImmutableChildrenView() {
        Action parent = Sequence.builder("parent")
                .child(Step.of("child", context -> {}))
                .build();
        MutableDescriptor root = builder.discover(parent);
        MutableDescriptor child = (MutableDescriptor) root.children().get(0);
        root.freeze();

        assertThat(root.children()).isSameAs(root.children());
        assertThat(child.children()).isSameAs(child.children());
    }

    @Test
    @DisplayName("freeze rejects post-freeze structural mutations")
    void freezeRejectsPostFreezeStructuralMutations() {
        MutableDescriptor root = builder.discover(Step.of("leaf", context -> {}));
        root.freeze();

        assertThatThrownBy(() -> root.addChild(new ConcreteDescriptor(Step.of("late-child", context -> {}))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Descriptor is frozen:");
        assertThatThrownBy(() ->
                        root.setSchedulerPriorityKey(SchedulerPriorityKey.root().child(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Descriptor is frozen:");
    }

    @Nested
    @DisplayName("Scope action")
    class ScopeAction {

        @Test
        @DisplayName("discovers scope with before, body, and after")
        void discoversScopeWithBeforeBodyAfter() {
            var before = Step.of("before", context -> {});
            var body = Step.of("body", context -> {});
            var after = Step.of("after", context -> {});
            var scope = Scope.builder("scope")
                    .before(before)
                    .body(body)
                    .after(after)
                    .build();
            var root = builder.discover(scope);

            assertThat(root.before()).isPresent();
            assertThat(((MutableDescriptor) root.before().orElseThrow()).action())
                    .isSameAs(before);
            assertThat(root.children()).hasSize(1);
            assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(body);
            assertThat(root.after()).isPresent();
            assertThat(((MutableDescriptor) root.after().orElseThrow()).action())
                    .isSameAs(after);
        }

        @Test
        @DisplayName("discovers scope with body only")
        void discoversScopeWithBodyOnly() {
            var body = Step.of("body", context -> {});
            var scope = Scope.builder("scope").body(body).build();
            var root = builder.discover(scope);

            assertThat(root.before()).isEmpty();
            assertThat(root.children()).hasSize(1);
            assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(body);
            assertThat(root.after()).isEmpty();
        }

        @Test
        @DisplayName("discovers scope with body and before only")
        void discoversScopeWithBodyAndBefore() {
            var before = Step.of("before", context -> {});
            var body = Step.of("body", context -> {});
            var scope = Scope.builder("scope").before(before).body(body).build();
            var root = builder.discover(scope);

            assertThat(root.before()).isPresent();
            assertThat(root.children()).hasSize(1);
            assertThat(root.after()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Static action")
    class StaticAction {

        @Test
        @DisplayName("discovers static with before, body, and after")
        void discoversStaticWithBeforeBodyAfter() {
            var before = Step.of("before", context -> {});
            var body = Step.of("body", context -> {});
            var after = Step.of("after", context -> {});
            var staticAction = Static.builder("static")
                    .before(before)
                    .body(body)
                    .after(after)
                    .build();
            var root = builder.discover(staticAction);

            assertThat(root.before()).isPresent();
            assertThat(((MutableDescriptor) root.before().orElseThrow()).action())
                    .isSameAs(before);
            assertThat(root.children()).hasSize(1);
            assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(body);
            assertThat(root.after()).isPresent();
            assertThat(((MutableDescriptor) root.after().orElseThrow()).action())
                    .isSameAs(after);
        }

        @Test
        @DisplayName("discovers static with body only")
        void discoversStaticWithBodyOnly() {
            var body = Step.of("body", context -> {});
            var staticAction = Static.builder("static").body(body).build();
            var root = builder.discover(staticAction);

            assertThat(root.before()).isEmpty();
            assertThat(root.children()).hasSize(1);
            assertThat(root.after()).isEmpty();
        }

        @Test
        @DisplayName("discovers static with body and after only")
        void discoversStaticWithBodyAndAfter() {
            var body = Step.of("body", context -> {});
            var after = Step.of("after", context -> {});
            var staticAction = Static.builder("static").body(body).after(after).build();
            var root = builder.discover(staticAction);

            assertThat(root.before()).isEmpty();
            assertThat(root.children()).hasSize(1);
            assertThat(root.after()).isPresent();
            assertThat(((MutableDescriptor) root.after().orElseThrow()).action())
                    .isSameAs(after);
        }
    }

    @Nested
    @DisplayName("Instance action")
    class InstanceAction {

        @Test
        @DisplayName("discovers instance with instantiate, body, and destroy descriptors")
        void discoversInstanceWithLifecycleDescriptors() {
            var body = Step.of("body", context -> {});
            var instance = Instance.builder("instance", Object::new).body(body).build();
            var root = builder.discover(instance);

            assertThat(root.before()).isPresent();
            assertThat(root.before().orElseThrow().action().displayName()).isEqualTo("[instantiate]");
            assertThat(root.children()).hasSize(1);
            assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(body);
            assertThat(root.after()).isPresent();
            assertThat(root.after().orElseThrow().action().displayName()).isEqualTo("[destroy]");
        }
    }

    @Nested
    @DisplayName("Parallel action")
    class ParallelAction {

        @Test
        @DisplayName("discovers parallel with multiple children")
        void discoversParallelWithMultipleChildren() {
            var child1 = Step.of("child1", context -> {});
            var child2 = Step.of("child2", context -> {});
            var child3 = Step.of("child3", context -> {});
            var parallel = Parallel.builder("parallel")
                    .child(child1)
                    .child(child2)
                    .child(child3)
                    .build();
            var root = builder.discover(parallel);

            assertThat(root.before()).isEmpty();
            assertThat(root.children()).hasSize(3);
            assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(child1);
            assertThat(((MutableDescriptor) root.children().get(1)).action()).isSameAs(child2);
            assertThat(((MutableDescriptor) root.children().get(2)).action()).isSameAs(child3);
            assertThat(root.after()).isEmpty();
        }

        @Test
        @DisplayName("discovers empty parallel")
        void discoversEmptyParallel() {
            var parallel = Parallel.builder("parallel").build();
            var root = builder.discover(parallel);

            assertThat(root.children()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Repeat action")
    class RepeatAction {

        @Test
        @DisplayName("discovers repeat with multiple iterations")
        void discoversRepeatWithMultipleIterations() {
            var body = Step.of("step", context -> {});
            var repeat = Repeat.builder("repeat").body(body).iterations(3).build();
            var root = builder.discover(repeat);

            assertThat(root.children()).hasSize(3);
            assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(body);
            assertThat(((MutableDescriptor) root.children().get(1)).action()).isSameAs(body);
            assertThat(((MutableDescriptor) root.children().get(2)).action()).isSameAs(body);
        }

        @Test
        @DisplayName("discovers repeat with single iteration")
        void discoversRepeatWithSingleIteration() {
            var body = Step.of("step", context -> {});
            var repeat = Repeat.builder("repeat").body(body).iterations(1).build();
            var root = builder.discover(repeat);

            assertThat(root.children()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Timeout action")
    class TimeoutAction {

        @Test
        @DisplayName("discovers timeout with body")
        void discoversTimeoutWithBody() {
            var body = Step.of("body", context -> {});
            var timeout = Timeout.builder("timeout")
                    .body(body)
                    .timeout(Duration.ofSeconds(1))
                    .build();
            var root = builder.discover(timeout);

            assertThat(root.children()).hasSize(1);
            assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(body);
        }
    }

    @Nested
    @DisplayName("deeply nested composites")
    class DeeplyNestedComposites {

        @Test
        @DisplayName("discovers Sequence within Sequence")
        void discoversSequenceWithinSequence() {
            var leaf = Step.of("leaf", context -> {});
            var inner = Sequence.builder("inner").child(leaf).build();
            var outer = Sequence.builder("outer").child(inner).build();
            var root = builder.discover(outer);

            assertThat(root.children()).hasSize(1);
            var outerChild = (MutableDescriptor) root.children().get(0);
            assertThat(outerChild.action()).isSameAs(inner);
            assertThat(outerChild.children()).hasSize(1);
            assertThat(((MutableDescriptor) outerChild.children().get(0)).action())
                    .isSameAs(leaf);
        }

        @Test
        @DisplayName("discovers Parallel within Sequence")
        void discoversParallelWithinSequence() {
            var leaf1 = Step.of("leaf1", context -> {});
            var leaf2 = Step.of("leaf2", context -> {});
            var parallel =
                    Parallel.builder("parallel").child(leaf1).child(leaf2).build();
            var sequence = Sequence.builder("sequence").child(parallel).build();
            var root = builder.discover(sequence);

            assertThat(root.children()).hasSize(1);
            var seqChild = (MutableDescriptor) root.children().get(0);
            assertThat(seqChild.action()).isSameAs(parallel);
            assertThat(seqChild.children()).hasSize(2);
        }

        @Test
        @DisplayName("discovers Repeat within Scope")
        void discoversRepeatWithinScope() {
            var step = Step.of("step", context -> {});
            var repeat = Repeat.builder("repeat").body(step).iterations(2).build();
            var scope = Scope.builder("scope").body(repeat).build();
            var root = builder.discover(scope);

            assertThat(root.children()).hasSize(1);
            var repeatDesc = (MutableDescriptor) root.children().get(0);
            assertThat(repeatDesc.action()).isSameAs(repeat);
            assertThat(repeatDesc.children()).hasSize(2);
        }

        @Test
        @DisplayName("discovers Timeout within Instance")
        void discoversTimeoutWithinInstance() {
            var step = Step.of("step", context -> {});
            var timeout = Timeout.builder("timeout")
                    .body(step)
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var instance =
                    Instance.builder("instance", Object::new).body(timeout).build();
            var root = builder.discover(instance);

            assertThat(root.children()).hasSize(1);
            var timeoutDesc = (MutableDescriptor) root.children().get(0);
            assertThat(timeoutDesc.action()).isSameAs(timeout);
            assertThat(timeoutDesc.children()).hasSize(1);
        }
    }
}
