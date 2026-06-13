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

import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;

@DisplayName("Context async scheduling")
@SuppressWarnings("removal")
class ContextAsyncSchedulingTest {

    private Scheduler scheduler;
    private InstanceHolder instanceHolder;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(1);
        instanceHolder = new InstanceHolder();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Test
    @DisplayName("scheduleAsync returns future completing with descriptor")
    void scheduleAsyncReturnsFuture() {
        var child = Step.of("child", context -> {});
        var parent = Sequence.builder("parent").child(child).build();
        var root = new DescriptorBuilder().discover(parent);
        var childDescriptor = (MutableDescriptor) root.children().get(0);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);
        root.markScheduled();

        var future = context.scheduleAsync(childDescriptor);

        assertThat(future).isNotNull();
        assertThat(future.join().isPassed()).isTrue();
    }

    @Test
    @DisplayName("scheduleAsync rejects null child")
    void scheduleAsyncRejectsNullChild() {
        var leaf = Step.of("leaf", context -> {});
        var root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        assertThatThrownBy(() -> context.scheduleAsync(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("child is null");
    }

    @Test
    @DisplayName("scheduleAsync rejects null mode")
    void scheduleAsyncRejectsNullMode() {
        var child = Step.of("child", context -> {});
        var parent = Sequence.builder("parent").child(child).build();
        var root = new DescriptorBuilder().discover(parent);
        var childDescriptor = (MutableDescriptor) root.children().get(0);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        assertThatThrownBy(() -> context.scheduleAsync(childDescriptor, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode is null");
    }

    @Test
    @DisplayName("scheduleAsync rejects descriptor from foreign context")
    void scheduleAsyncRejectsForeignDescriptor() {
        var leaf = Step.of("leaf", context -> {});
        var root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);
        root.markScheduled();

        var foreignLeaf = Step.of("foreign", ignored -> {});
        var foreignRoot = new DescriptorBuilder().discover(foreignLeaf);
        foreignRoot.markScheduled();

        var foreignDescriptor = (MutableDescriptor) foreignRoot;

        assertThatThrownBy(() -> context.scheduleAsync(foreignDescriptor)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("descriptor status can be set directly")
    void descriptorStatusCanBeSetDirectly() {
        var leaf = Step.of("leaf", context -> {});
        var root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        root.setStatus(Status.RUNNING);

        assertThat(root.status()).isEqualTo(Status.RUNNING);
    }

    @Test
    @DisplayName("getInstance returns empty when no instance set")
    void getInstanceReturnsEmptyWhenNoInstance() {
        var leaf = Step.of("leaf", context -> {});
        var root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        assertThat(context.instance(String.class)).isEmpty();
    }

    @Test
    @DisplayName("getInstance returns present when instance set")
    void getInstanceReturnsPresentWhenInstanceSet() {
        var leaf = Step.of("leaf", context -> {});
        var root = new DescriptorBuilder().discover(leaf);
        instanceHolder.set("hello");
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        assertThat(context.instance(String.class)).contains("hello");
    }
}
