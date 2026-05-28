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

package org.paramixel.api.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import nonapi.org.paramixel.action.ConcreteContext;
import nonapi.org.paramixel.support.Arguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;

@DisplayName("Custom action")
class CustomActionTest {

    @Test
    @DisplayName("custom action without children executes correctly")
    void customActionWithoutChildrenExecutesCorrectly() {
        var messages = new ArrayList<String>();
        var events = new ArrayList<String>();
        var listener = new RecordingListener(events);

        Action<?> action = new SimplePrintAction("solo", messages);
        var result = Runner.builder().listener(listener).build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root).isNotNull();
        assertThat(root.metadata().status().isPassed()).isTrue();
        assertThat(messages).containsExactly("solo executed");
        assertThat(events).contains("before: solo", "after: solo [PASSED]");
    }

    @Test
    @DisplayName("custom action with five children executes sequentially")
    void customActionWithFiveChildrenExecutesSequentially() {
        var messages = new ArrayList<String>();
        var events = new ArrayList<String>();
        var listener = new RecordingListener(events);

        var children = new ArrayList<Action<?>>();
        for (int i = 1; i <= 5; i++) {
            children.add(new SimplePrintAction("child-" + i, messages));
        }

        Action<?> action = new SequentialPrintAction("parent", children, messages);
        var result = Runner.builder().listener(listener).build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root).isNotNull();
        assertThat(root.metadata().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(5);

        for (Descriptor child : root.children()) {
            assertThat(child.metadata().status().isPassed()).isTrue();
        }

        assertThat(events)
                .contains("before: parent", "after: parent [PASSED]")
                .contains("before: child-1", "after: child-1 [PASSED]")
                .contains("before: child-2", "after: child-2 [PASSED]")
                .contains("before: child-3", "after: child-3 [PASSED]")
                .contains("before: child-4", "after: child-4 [PASSED]")
                .contains("before: child-5", "after: child-5 [PASSED]");

        assertThat(events.indexOf("before: parent")).isLessThan(events.indexOf("before: child-1"));
        assertThat(events.indexOf("after: child-5 [PASSED]")).isLessThan(events.indexOf("after: parent [PASSED]"));

        assertThat(messages)
                .containsExactly(
                        "parent before-children",
                        "child-1 executed",
                        "child-2 executed",
                        "child-3 executed",
                        "child-4 executed",
                        "child-5 executed",
                        "parent after-children");
    }

    private static final class SimplePrintAction implements Action<Void> {

        private final String name;
        private final List<String> messages;

        SimplePrintAction(final String name, final List<String> messages) {
            Objects.requireNonNull(name, "name is null");
            this.name = Arguments.requireNonBlank(name, "name is blank");
            this.messages = Objects.requireNonNull(messages);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String kind() {
            return "SimplePrintAction";
        }

        @Override
        public void execute(final Context context) {
            Objects.requireNonNull(context);
            var descriptor = context.descriptor();
            var listener = context.listener();
            listener.onBeforeExecution(descriptor);
            context.setStatus(Status.RUNNING);
            try {
                messages.add(name + " executed");
                context.setStatus(Status.PASSED);
            } catch (Throwable t) {
                context.setStatus(Status.fromThrowable(t));
            }
            listener.onAfterExecution(descriptor);
        }
    }

    private static final class SequentialPrintAction implements Action<Void> {

        private final String name;
        private final List<Action<?>> children;
        private final List<String> messages;

        SequentialPrintAction(final String name, final List<Action<?>> children, final List<String> messages) {
            Objects.requireNonNull(name, "name is null");
            this.name = Arguments.requireNonBlank(name, "name is blank");
            this.children = List.copyOf(Objects.requireNonNull(children));
            this.messages = Objects.requireNonNull(messages);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String kind() {
            return "SequentialPrintAction";
        }

        @Override
        public List<Action<?>> children() {
            return children;
        }

        @Override
        public void execute(final Context context) {
            Objects.requireNonNull(context);
            var descriptor = context.descriptor();
            var listener = context.listener();
            listener.onBeforeExecution(descriptor);
            context.setStatus(Status.RUNNING);
            try {
                var mode = descriptor.metadata().mode();
                if (mode != Mode.RUN) {
                    if (context instanceof ConcreteContext concrete) {
                        concrete.runChildren(mode);
                    }
                    context.setStatus(mode.toStatus());
                } else {
                    messages.add(name + " before-children");
                    var childDescriptors = context.descriptor().children();
                    var completed = new ArrayList<Descriptor>();
                    for (Descriptor child : childDescriptors) {
                        if (context instanceof ConcreteContext concrete) {
                            completed.add(concrete.runChild(child, Mode.RUN));
                        }
                    }
                    messages.add(name + " after-children");
                    context.setStatus(Status.aggregate(completed));
                }
            } catch (Throwable t) {
                context.setStatus(Status.fromThrowable(t));
            }
            listener.onAfterExecution(descriptor);
        }
    }

    private static final class RecordingListener implements Listener {

        private final List<String> events;

        RecordingListener(final List<String> events) {
            this.events = Objects.requireNonNull(events);
        }

        @Override
        public void initialize(final Configuration configuration) {}

        @Override
        public void onDiscoveryStarted() {}

        @Override
        public void onRunStarted() {
            events.add("run-started");
        }

        @Override
        public void onDiscoveryCompleted(final Descriptor root) {
            events.add("discovery-completed: " + root.metadata().name());
        }

        @Override
        public void onBeforeExecution(final Descriptor descriptor) {
            events.add("before: " + descriptor.metadata().name());
        }

        @Override
        public void onAfterExecution(final Descriptor descriptor) {
            events.add("after: " + descriptor.metadata().name() + " ["
                    + descriptor.metadata().status().name() + "]");
        }

        @Override
        public void onRunCompleted(final Result result) {
            events.add("run-completed");
        }
    }
}
