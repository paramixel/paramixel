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
import java.util.concurrent.CompletableFuture;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.action.DescriptorBuilder;
import nonapi.org.paramixel.action.MutableDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Status;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Context;
import org.paramixel.api.action.Mode;
import org.paramixel.api.action.Step;

@DisplayName("Context async scheduling")
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
        Action<?> child = Step.of("child", obj -> {});
        Action<?> parent = new CompositeTestAction("parent", child);
        MutableDescriptor root = new DescriptorBuilder().discover(parent);
        var childDescriptor = (MutableDescriptor) root.children().get(0);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);
        root.markScheduled(Mode.RUN);

        CompletableFuture<Descriptor> future = context.scheduleAsync(ExecutionRequest.run(childDescriptor));

        assertThat(future).isNotNull();
        assertThat(future.join().metadata().status()).isEqualTo(Status.PASSED);
    }

    @Test
    @DisplayName("scheduleAsync rejects null request")
    void scheduleAsyncRejectsNullRequest() {
        Action<?> leaf = Step.of("leaf", obj -> {});
        MutableDescriptor root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        assertThatThrownBy(() -> context.scheduleAsync(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request is null");
    }

    @Test
    @DisplayName("scheduleAsync rejects non-direct-child descriptor")
    void scheduleAsyncRejectsNonDirectChild() {
        Action<?> grandchild = Step.of("grandchild", obj -> {});
        Action<?> childAction = new CompositeTestAction("child", grandchild);
        Action<?> rootAction = new CompositeTestAction("root", childAction);
        MutableDescriptor root = new DescriptorBuilder().discover(rootAction);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);
        root.markScheduled(Mode.RUN);

        var childDescriptor = (MutableDescriptor) root.children().get(0);
        var grandchildDescriptor =
                (MutableDescriptor) childDescriptor.children().get(0);

        assertThatThrownBy(() -> context.scheduleAsync(ExecutionRequest.run(grandchildDescriptor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("can only schedule directly attached descriptors");
    }

    @Test
    @DisplayName("scheduleAsync rejects foreign descriptor")
    void scheduleAsyncRejectsForeignDescriptor() {
        Action<?> foreign = Step.of("foreign", obj -> {});
        MutableDescriptor foreignDescriptor = new DescriptorBuilder().discover(foreign);
        Action<?> rootAction = Step.of("root", obj -> {});
        MutableDescriptor root = new DescriptorBuilder().discover(rootAction);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        assertThatThrownBy(() -> context.scheduleAsync(ExecutionRequest.run(foreignDescriptor)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("setStatus delegates to descriptor")
    void setStatusDelegatesToDescriptor() {
        Action<?> leaf = Step.of("leaf", obj -> {});
        MutableDescriptor root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        context.setStatus(Status.RUNNING);

        assertThat(root.metadata().status()).isEqualTo(Status.RUNNING);
    }

    @Test
    @DisplayName("getInstance returns empty when no instance set")
    void getInstanceReturnsEmptyWhenNoInstance() {
        Action<?> leaf = Step.of("leaf", obj -> {});
        MutableDescriptor root = new DescriptorBuilder().discover(leaf);
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        assertThat(context.instance(String.class)).isEmpty();
    }

    @Test
    @DisplayName("getInstance returns present when instance set")
    void getInstanceReturnsPresentWhenInstanceSet() {
        Action<?> leaf = Step.of("leaf", obj -> {});
        MutableDescriptor root = new DescriptorBuilder().discover(leaf);
        instanceHolder.set("hello");
        var context = new ConcreteContext(
                Configuration.defaultConfiguration(), Listener.defaultListener(), root, scheduler, instanceHolder);

        assertThat(context.instance(String.class)).contains("hello");
    }

    private static final class CompositeTestAction implements Action<Void> {

        private final String name;
        private final Action<?>[] children;

        CompositeTestAction(final String name, final Action<?>... children) {
            this.name = name;
            this.children = children;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String kind() {
            return "CompositeTestAction";
        }

        @Override
        public List<Action<?>> children() {
            return List.of(children);
        }

        @Override
        public void execute(final Context context) {}
    }
}
