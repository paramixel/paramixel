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

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Context;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.CycleDetectedException;

@DisplayName("DescriptorBuilder")
class DescriptorBuilderTest {

    private final DescriptorBuilder builder = new DescriptorBuilder(Configuration.defaultConfiguration());

    @Test
    @DisplayName("discovers single leaf action")
    void discoversSingleLeafAction() {
        Action<?> action = Step.of("leaf", obj -> {});
        MutableDescriptor root = builder.discover(action);

        assertThat(root).isNotNull();
        assertThat(root.action()).isSameAs(action);
        assertThat(root.children()).isEmpty();
    }

    @Test
    @DisplayName("discovers composite action with children")
    void discoversCompositeWithChildren() {
        Action<?> child1 = Step.of("child1", obj -> {});
        Action<?> child2 = Step.of("child2", obj -> {});
        Action<?> parent = new CompositeAction("parent", child1, child2);
        MutableDescriptor root = builder.discover(parent);

        assertThat(root.children()).hasSize(2);
        assertThat(((MutableDescriptor) root.children().get(0)).action()).isSameAs(child1);
        assertThat(((MutableDescriptor) root.children().get(1)).action()).isSameAs(child2);
    }

    @Test
    @DisplayName("child descriptors have parent reference")
    void childDescriptorsHaveParent() {
        Action<?> child = Step.of("child", obj -> {});
        Action<?> parent = new CompositeAction("parent", child);
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
    @DisplayName("detects direct self-cycle")
    void detectsDirectSelfCycle() {
        var cyclic = new CyclicAction("cyclic");
        assertThatThrownBy(() -> builder.discover(cyclic)).isInstanceOf(CycleDetectedException.class);
    }

    @Test
    @DisplayName("detects indirect cycle")
    void detectsIndirectCycle() {
        var cyclic = new IndirectCyclicAction("indirect");
        assertThatThrownBy(() -> builder.discover(cyclic)).isInstanceOf(CycleDetectedException.class);
    }

    @Test
    @DisplayName("same action instance reused in multiple positions produces independent descriptors")
    void sameActionInstanceReusedProducesIndependentDescriptors() {
        Action<?> shared = Step.of("shared", obj -> {});
        Action<?> parent = new CompositeAction("parent", shared, shared);
        MutableDescriptor root = builder.discover(parent);

        assertThat(root.children()).hasSize(2);
        assertThat(root.children().get(0)).isNotSameAs(root.children().get(1));
        assertThat(((MutableDescriptor) root.children().get(0)).action())
                .isSameAs(((MutableDescriptor) root.children().get(1)).action());
    }

    @Test
    @DisplayName("rejects null child in discover")
    void rejectsNullChildInDiscover() {
        Action<?> parent = new NullDiscoveringAction("bad");
        assertThatThrownBy(() -> builder.discover(parent)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("root descriptor starts with PENDING status")
    void rootDescriptorStartsPending() {
        Action<?> action = Step.of("leaf", obj -> {});
        MutableDescriptor root = builder.discover(action);

        assertThat(root.metadata().status().isPending()).isTrue();
    }

    @Test
    @DisplayName("discover leaves descriptor tree mutable until frozen")
    void discoverLeavesDescriptorTreeMutableUntilFrozen() {
        Action<?> parent = new CompositeAction("parent", Step.of("child", obj -> {}));
        MutableDescriptor root = builder.discover(parent);
        MutableDescriptor child = (MutableDescriptor) root.children().get(0);

        assertThat(root.isFrozen()).isFalse();
        assertThat(child.isFrozen()).isFalse();
    }

    @Test
    @DisplayName("freeze caches immutable children view")
    void freezeCachesImmutableChildrenView() {
        Action<?> parent = new CompositeAction("parent", Step.of("child", obj -> {}));
        MutableDescriptor root = builder.discover(parent);
        MutableDescriptor child = (MutableDescriptor) root.children().get(0);
        root.freeze();

        assertThat(root.children()).isSameAs(root.children());
        assertThat(child.children()).isSameAs(child.children());
    }

    @Test
    @DisplayName("freeze rejects post-freeze structural mutations")
    void freezeRejectsPostFreezeStructuralMutations() {
        MutableDescriptor root = builder.discover(Step.of("leaf", obj -> {}));
        root.freeze();

        assertThatThrownBy(() -> root.addChild(new ConcreteDescriptor(Step.of("late-child", obj -> {}))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Descriptor is frozen:");
        assertThatThrownBy(() ->
                        root.setSchedulerPriorityKey(SchedulerPriorityKey.root().child(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Descriptor is frozen:");
    }

    private static final class CompositeAction implements Action<Void> {

        private final String name;
        private final Action<?>[] children;

        CompositeAction(final String name, final Action<?>... children) {
            this.name = name;
            this.children = children;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String kind() {
            return "CompositeAction";
        }

        @Override
        public List<Action<?>> children() {
            return List.of(children);
        }

        @Override
        public void execute(final Context context) {}
    }

    private static final class CyclicAction implements Action<Void> {

        private final String name;

        CyclicAction(final String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String kind() {
            return "CyclicAction";
        }

        @Override
        public List<Action<?>> children() {
            return List.of(this);
        }

        @Override
        public void execute(final Context context) {}
    }

    private static final class IndirectCyclicAction implements Action<Void> {

        private final String name;
        private final Action<?> wrapper;

        IndirectCyclicAction(final String name) {
            this.name = name;
            this.wrapper = new Action<Void>() {
                @Override
                public String name() {
                    return "wrapper";
                }

                @Override
                public String kind() {
                    return "Wrapper";
                }

                @Override
                public List<Action<?>> children() {
                    return List.of(IndirectCyclicAction.this);
                }

                @Override
                public void execute(final Context ctx) {}
            };
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String kind() {
            return "IndirectCyclicAction";
        }

        @Override
        public List<Action<?>> children() {
            return List.of(wrapper);
        }

        @Override
        public void execute(final Context context) {}
    }

    private static final class NullDiscoveringAction implements Action<Void> {

        private final String name;

        NullDiscoveringAction(final String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String kind() {
            return "NullDiscoveringAction";
        }

        @Override
        public List<Action<?>> children() {
            return Collections.singletonList(null);
        }

        @Override
        public void execute(final Context context) {}
    }
}
