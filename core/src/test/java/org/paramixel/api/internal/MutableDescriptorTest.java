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

package org.paramixel.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Step;
import org.paramixel.api.internal.action.ConcreteDescriptor;
import org.paramixel.api.internal.action.MutableDescriptor;
import org.paramixel.api.internal.action.SchedulerPriorityKey;

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
}
