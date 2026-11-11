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

import java.util.List;
import nonapi.org.paramixel.action.ConcreteDescriptor;
import nonapi.org.paramixel.action.TestDescriptors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

@DisplayName("MutableDescriptor")
class MutableDescriptorTest {

    @Test
    @DisplayName("single-arg constructor creates descriptor with action")
    void singleArgConstructorCreatesDescriptorWithAction() {
        var action = Step.step("test", v -> {});
        var descriptor = new ConcreteDescriptor(action);

        assertThat(descriptor.action()).isSameAs(action);
        assertThat(descriptor.action().displayName()).isEqualTo("test");
    }

    @Test
    @DisplayName("parent constructor creates child descriptor with parent reference")
    void parentConstructorCreatesChildWithParent() {
        var parent = new ConcreteDescriptor(Step.step("parent", v -> {}));
        var child = new ConcreteDescriptor(parent, Step.step("child", v -> {}));

        assertThat(child.parent()).containsSame(parent);
        assertThat(child.action().displayName()).isEqualTo("child");
    }

    @Test
    @DisplayName("root descriptor has no parent")
    void rootDescriptorHasNoParent() {
        var descriptor = new ConcreteDescriptor(Step.step("root", v -> {}));

        assertThat(descriptor.parent()).isEmpty();
    }

    @Test
    @DisplayName("before and after are separate slots not included in children")
    void beforeAndAfterAreSeparateFromChildren() {
        var before = TestDescriptors.of(Step.step("before", v -> {}));
        var child = TestDescriptors.of(Step.step("child", v -> {}));
        var after = TestDescriptors.of(Step.step("after", v -> {}));

        var parent = TestDescriptors.tree(Step.step("parent", v -> {}), before, List.of(child), after);

        assertThat(parent.before()).containsSame(before);
        assertThat(parent.after()).containsSame(after);
        assertThat(parent.children()).containsExactly(child);
        assertThat(parent.path()).isEqualTo("parent");
        assertThat(child.path()).isEqualTo("parent / child");
    }

    @Test
    @DisplayName("attach rejects a second parent")
    void attachRejectsSecondParent() {
        var root = TestDescriptors.of(Step.step("root", v -> {}));
        var other = TestDescriptors.of(Step.step("other", v -> {}));
        var child = TestDescriptors.of(Step.step("child", v -> {}));
        child.attach(root);

        assertThatThrownBy(() -> child.attach(other))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already attached");
    }

    @Nested
    @DisplayName("depth()")
    class DepthTests {

        @Test
        @DisplayName("root descriptor has depth 0")
        void rootDescriptorHasDepthZero() {
            var root = new ConcreteDescriptor(Step.step("root", v -> {}));

            assertThat(root.depth()).isZero();
        }

        @Test
        @DisplayName("direct child has depth 1")
        void directChildHasDepthOne() {
            var root = new ConcreteDescriptor(Step.step("root", v -> {}));
            var child = new ConcreteDescriptor(root, Step.step("child", v -> {}));

            assertThat(child.depth()).isEqualTo(1);
        }

        @Test
        @DisplayName("grandchild has depth 2")
        void grandchildHasDepthTwo() {
            var root = new ConcreteDescriptor(Step.step("root", v -> {}));
            var child = new ConcreteDescriptor(root, Step.step("child", v -> {}));
            var grandchild = new ConcreteDescriptor(child, Step.step("grandchild", v -> {}));

            assertThat(grandchild.depth()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("isCoordinationAction()")
    class IsCoordinationActionTests {

        @Test
        @DisplayName("returns true when wrapping a Parallel action")
        void returnsTrueWhenWrappingParallel() {
            var descriptor =
                    new ConcreteDescriptor(Parallel.parallel("parallel", "t0").build());

            assertThat(descriptor.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when wrapping a Sequential action")
        void returnsTrueWhenWrappingSequential() {
            var descriptor =
                    new ConcreteDescriptor(Sequential.sequential("sequential").build());

            assertThat(descriptor.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when wrapping an Isolated action")
        void returnsTrueWhenWrappingIsolated() {
            var descriptor = new ConcreteDescriptor(Isolated.isolated("isolated", "lock")
                    .body(Step.step("body", v -> {}))
                    .build());

            assertThat(descriptor.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when before slot is present")
        void returnsTrueWhenBeforeSlotIsPresent() {
            var before = TestDescriptors.of(Step.step("before", v -> {}));
            var parent = TestDescriptors.tree(Step.step("parent", v -> {}), before, List.of(), null);

            assertThat(parent.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when after slot is present")
        void returnsTrueWhenAfterSlotIsPresent() {
            var after = TestDescriptors.of(Step.step("after", v -> {}));
            var parent = TestDescriptors.tree(Step.step("parent", v -> {}), null, List.of(), after);

            assertThat(parent.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when children list is non-empty")
        void returnsTrueWhenChildrenListIsNonEmpty() {
            var child = TestDescriptors.of(Step.step("child", v -> {}));
            var parent = TestDescriptors.tree(Step.step("parent", v -> {}), List.of(child));

            assertThat(parent.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns false for leaf Step with no children or slots")
        void returnsFalseForLeafStepWithNoChildrenOrSlots() {
            var leaf = new ConcreteDescriptor(Step.step("leaf", v -> {}));

            assertThat(leaf.isCoordinationAction()).isFalse();
        }
    }

    @Nested
    @DisplayName("isLeafAction()")
    class IsLeafActionTests {

        @Test
        @DisplayName("returns true for Step action")
        void returnsTrueForStepAction() {
            var descriptor = new ConcreteDescriptor(Step.step("step", v -> {}));

            assertThat(descriptor.isLeafAction()).isTrue();
        }

        @Test
        @DisplayName("returns false for Parallel action")
        void returnsFalseForParallelAction() {
            var descriptor =
                    new ConcreteDescriptor(Parallel.parallel("parallel", "t1").build());

            assertThat(descriptor.isLeafAction()).isFalse();
        }
    }
}
