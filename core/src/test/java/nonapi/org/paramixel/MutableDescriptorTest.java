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
import nonapi.org.paramixel.action.MutableDescriptor;
import nonapi.org.paramixel.action.SchedulerPriorityKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Step;

@DisplayName("MutableDescriptor")
class MutableDescriptorTest {

    @Test
    @DisplayName("single-arg constructor creates descriptor with action")
    void singleArgConstructorCreatesDescriptorWithAction() {
        var action = Step.of("test", v -> {});
        MutableDescriptor descriptor = new ConcreteDescriptor(action);

        assertThat(descriptor.action()).isSameAs(action);
        assertThat(descriptor.metadata().name()).isEqualTo("test");
    }

    @Test
    @DisplayName("parent constructor creates child descriptor with parent reference")
    void parentConstructorCreatesChildWithParent() {
        MutableDescriptor parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        var child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));

        assertThat(child.parent()).containsSame(parent);
        assertThat(child.metadata().name()).isEqualTo("child");
    }

    @Test
    @DisplayName("root descriptor has no parent")
    void rootDescriptorHasNoParent() {
        MutableDescriptor descriptor = new ConcreteDescriptor(Step.of("root", v -> {}));

        assertThat(descriptor.parent()).isEmpty();
    }

    @Test
    @DisplayName("addChild assigns stable scheduler priority keys from tree position")
    void addChildAssignsStableSchedulerPriorityKeysFromTreePosition() {
        MutableDescriptor root = new ConcreteDescriptor(Step.of("root", v -> {}));
        MutableDescriptor left = new ConcreteDescriptor(root, Step.of("left", v -> {}));
        MutableDescriptor right = new ConcreteDescriptor(root, Step.of("right", v -> {}));
        MutableDescriptor leftChild = new ConcreteDescriptor(left, Step.of("left-child", v -> {}));

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
        MutableDescriptor parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        MutableDescriptor before = new ConcreteDescriptor(parent, Step.of("before", v -> {}));
        MutableDescriptor child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));

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
        MutableDescriptor parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        MutableDescriptor child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));
        MutableDescriptor after = new ConcreteDescriptor(parent, Step.of("after", v -> {}));

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
        MutableDescriptor parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        MutableDescriptor before = new ConcreteDescriptor(parent, Step.of("before", v -> {}));
        MutableDescriptor child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));
        MutableDescriptor after = new ConcreteDescriptor(parent, Step.of("after", v -> {}));

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
        MutableDescriptor parent = new ConcreteDescriptor(Step.of("parent", v -> {}));
        MutableDescriptor before = new ConcreteDescriptor(parent, Step.of("before", v -> {}));
        MutableDescriptor child = new ConcreteDescriptor(parent, Step.of("child", v -> {}));
        MutableDescriptor after = new ConcreteDescriptor(parent, Step.of("after", v -> {}));

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
        MutableDescriptor root = new ConcreteDescriptor(Step.of("root", v -> {}));
        root.freeze();

        assertThatThrownBy(() -> root.setBefore(new ConcreteDescriptor(Step.of("late-before", v -> {}))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Descriptor is frozen:");
        assertThatThrownBy(() -> root.setAfter(new ConcreteDescriptor(Step.of("late-after", v -> {}))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Descriptor is frozen:");
    }
}
