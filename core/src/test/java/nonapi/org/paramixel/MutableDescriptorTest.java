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
import nonapi.org.paramixel.action.SchedulerPriorityKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Assert;
import org.paramixel.api.action.Delay;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

@DisplayName("MutableDescriptor")
@SuppressWarnings("removal")
class MutableDescriptorTest {

    @Test
    @DisplayName("single-arg constructor creates descriptor with action")
    void singleArgConstructorCreatesDescriptorWithAction() {
        var action = Step.of("test", v -> {});
        var descriptor = new ConcreteDescriptor(action);

        assertThat(descriptor.action()).isSameAs(action);
        assertThat(descriptor.action().displayName()).isEqualTo("test");
    }

    @Test
    @DisplayName("parent constructor creates child descriptor with parent reference")
    void parentConstructorCreatesChildWithParent() {
        var parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        var child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));

        assertThat(child.parent()).containsSame(parent);
        assertThat(child.action().displayName()).isEqualTo("child");
    }

    @Test
    @DisplayName("root descriptor has no parent")
    void rootDescriptorHasNoParent() {
        var descriptor = new ConcreteDescriptor(Step.of("root", v -> {}));

        assertThat(descriptor.parent()).isEmpty();
    }

    @Test
    @DisplayName("addChild assigns stable scheduler priority keys from tree position")
    void addChildAssignsStableSchedulerPriorityKeysFromTreePosition() {
        var root = new ConcreteDescriptor(Step.of("root", v -> {}));
        var left = new ConcreteDescriptor(root, Step.of("left", v -> {}));
        var right = new ConcreteDescriptor(root, Step.of("right", v -> {}));
        var leftChild = new ConcreteDescriptor(left, Step.of("left-child", v -> {}));

        root.addChild(left);
        root.addChild(right);
        left.addChild(leftChild);

        assertThat(root.schedulerPriorityKey().compareTo(SchedulerPriorityKey.root()))
                .isZero();
        assertThat(left.schedulerPriorityKey()
                        .compareTo(SchedulerPriorityKey.root().child(0)))
                .isZero();
        assertThat(right.schedulerPriorityKey()
                        .compareTo(SchedulerPriorityKey.root().child(1)))
                .isZero();
        assertThat(leftChild
                        .schedulerPriorityKey()
                        .compareTo(SchedulerPriorityKey.root().child(0).child(0)))
                .isZero();
    }

    @Test
    @DisplayName("setBefore assigns before slot and priority key at offset 0")
    void setBeforeAssignsBeforeSlotAndPriorityKey() {
        var parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        var before = new ConcreteDescriptor(parent, Step.of("before", v -> {}));
        var child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));

        parent.setBefore(before);
        parent.addChild(child);

        assertThat(parent.before()).containsSame(before);
        assertThat(parent.children()).hasSize(1);
        assertThat(before.schedulerPriorityKey()
                        .compareTo(SchedulerPriorityKey.root().child(0)))
                .isZero();
        assertThat(child.schedulerPriorityKey()
                        .compareTo(SchedulerPriorityKey.root().child(1)))
                .isZero();
    }

    @Test
    @DisplayName("setAfter assigns after slot and priority key after children")
    void setAfterAssignsAfterSlotAndPriorityKey() {
        var parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        var child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));
        var after = new ConcreteDescriptor(parent, Step.of("after", v -> {}));

        parent.addChild(child);
        parent.setAfter(after);

        assertThat(parent.after()).containsSame(after);
        assertThat(parent.children()).hasSize(1);
        assertThat(child.schedulerPriorityKey()
                        .compareTo(SchedulerPriorityKey.root().child(0)))
                .isZero();
        assertThat(after.schedulerPriorityKey()
                        .compareTo(SchedulerPriorityKey.root().child(1)))
                .isZero();
    }

    @Test
    @DisplayName("before and after are separate slots not included in children")
    void beforeAndAfterAreSeparateFromChildren() {
        var parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        var before = new ConcreteDescriptor(parent, Step.of("before", v -> {}));
        var child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));
        var after = new ConcreteDescriptor(parent, Step.of("after", v -> {}));

        parent.setBefore(before);
        parent.addChild(child);
        parent.setAfter(after);

        assertThat(parent.before()).containsSame(before);
        assertThat(parent.after()).containsSame(after);
        assertThat(parent.children()).hasSize(1);
        assertThat(parent.children().get(0)).isSameAs(child);
    }

    @Test
    @DisplayName("freeze freezes before children and after recursively")
    void freezeFreezesBeforeChildrenAndAfterRecursively() {
        var parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        var before = new ConcreteDescriptor(parent, Step.of("before", v -> {}));
        var child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));
        var after = new ConcreteDescriptor(parent, Step.of("after", v -> {}));

        parent.setBefore(before);
        parent.addChild(child);
        parent.setAfter(after);
        parent.freeze();

        assertThat(parent.isFrozen()).isTrue();
        assertThat(before.isFrozen()).isTrue();
        assertThat(child.isFrozen()).isTrue();
        assertThat(after.isFrozen()).isTrue();
    }

    @Test
    @DisplayName("freeze rejects post-freeze setBefore and setAfter")
    void freezeRejectsPostFreezeSetBeforeAndSetAfter() {
        var root = new ConcreteDescriptor(Step.of("root", v -> {}));
        root.freeze();

        assertThatThrownBy(() -> root.setBefore(new ConcreteDescriptor(Step.of("late-before", v -> {}))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Descriptor is frozen:");
        assertThatThrownBy(() -> root.setAfter(new ConcreteDescriptor(Step.of("late-after", v -> {}))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Descriptor is frozen:");
    }

    @Nested
    @DisplayName("depth()")
    class DepthTests {

        @Test
        @DisplayName("root descriptor has depth 0")
        void rootDescriptorHasDepthZero() {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));

            assertThat(root.depth()).isZero();
        }

        @Test
        @DisplayName("direct child has depth 1")
        void directChildHasDepthOne() {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));
            var child = new ConcreteDescriptor(root, Step.of("child", v -> {}));

            assertThat(child.depth()).isEqualTo(1);
        }

        @Test
        @DisplayName("grandchild has depth 2")
        void grandchildHasDepthTwo() {
            var root = new ConcreteDescriptor(Step.of("root", v -> {}));
            var child = new ConcreteDescriptor(root, Step.of("child", v -> {}));
            var grandchild = new ConcreteDescriptor(child, Step.of("grandchild", v -> {}));

            assertThat(grandchild.depth()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("isCoordinationAction()")
    class IsCoordinationActionTests {

        @Test
        @DisplayName("returns true when wrapping a Parallel action")
        void returnsTrueWhenWrappingParallel() {
            var descriptor = new ConcreteDescriptor(Parallel.builder("parallel").build());

            assertThat(descriptor.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when wrapping a Sequence action")
        void returnsTrueWhenWrappingSequence() {
            var descriptor = new ConcreteDescriptor(Sequence.builder("sequence").build());

            assertThat(descriptor.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when before slot is present")
        void returnsTrueWhenBeforeSlotIsPresent() {
            var parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
            var before = new ConcreteDescriptor(parent, Step.of("before", v -> {}));
            parent.setBefore(before);

            assertThat(parent.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when after slot is present")
        void returnsTrueWhenAfterSlotIsPresent() {
            var parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
            var after = new ConcreteDescriptor(parent, Step.of("after", v -> {}));
            parent.setAfter(after);

            assertThat(parent.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns true when children list is non-empty")
        void returnsTrueWhenChildrenListIsNonEmpty() {
            var parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
            parent.addChild(new ConcreteDescriptor(parent, Step.of("child", v -> {})));

            assertThat(parent.isCoordinationAction()).isTrue();
        }

        @Test
        @DisplayName("returns false for leaf Step with no children or slots")
        void returnsFalseForLeafStepWithNoChildrenOrSlots() {
            var leaf = new ConcreteDescriptor(Step.of("leaf", v -> {}));

            assertThat(leaf.isCoordinationAction()).isFalse();
        }
    }

    @Nested
    @DisplayName("isLeafAction()")
    class IsLeafActionTests {

        @Test
        @DisplayName("returns true for Step action")
        void returnsTrueForStepAction() {
            var descriptor = new ConcreteDescriptor(Step.of("step", v -> {}));

            assertThat(descriptor.isLeafAction()).isTrue();
        }

        @Test
        @DisplayName("returns true for Assert action")
        void returnsTrueForAssertAction() {
            var descriptor = new ConcreteDescriptor(Assert.of("assert", true, true));

            assertThat(descriptor.isLeafAction()).isTrue();
        }

        @Test
        @DisplayName("returns true for Delay action")
        void returnsTrueForDelayAction() {
            var descriptor = new ConcreteDescriptor(Delay.of("delay", 1L));

            assertThat(descriptor.isLeafAction()).isTrue();
        }

        @Test
        @DisplayName("returns false for Parallel action")
        void returnsFalseForParallelAction() {
            var descriptor = new ConcreteDescriptor(Parallel.builder("parallel").build());

            assertThat(descriptor.isLeafAction()).isFalse();
        }
    }
}
