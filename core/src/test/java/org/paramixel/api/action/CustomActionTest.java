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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;

@DisplayName("Custom action")
@SuppressWarnings("removal")
class CustomActionTest {

    @Test
    @DisplayName("custom action without children executes correctly")
    void customActionWithoutChildrenExecutesCorrectly() {
        var messages = new ArrayList<String>();
        var events = new ArrayList<String>();
        var listener = new RecordingListener(events);

        var action = Step.of("solo", context -> {
            messages.add("solo executed");
        });
        var result = Runner.builder().listener(listener).build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root).isNotNull();
        assertThat(root.isPassed()).isTrue();
        assertThat(messages).containsExactly("solo executed");
        assertThat(events).contains("before: solo", "after: solo [PASSED]");
    }

    @Test
    @DisplayName("custom action with five children executes sequentially")
    void customActionWithFiveChildrenExecutesSequencely() {
        var messages = new ArrayList<String>();
        var events = new ArrayList<String>();
        var listener = new RecordingListener(events);

        var body = Sequence.builder("body");
        for (int i = 1; i <= 5; i++) {
            final int index = i;
            body.child(Step.of("child-" + index, context -> {
                messages.add("child-" + index + " executed");
            }));
        }

        var action = Static.builder("parent")
                .before(Step.of("parent-setup", context -> {
                    messages.add("parent before-children");
                }))
                .body(body.build())
                .after(Step.of("parent-teardown", context -> {
                    messages.add("parent after-children");
                }))
                .build();

        var root = Runner.builder()
                .listener(listener)
                .build()
                .run(action)
                .descriptor()
                .orElseThrow();

        assertThat(root).isNotNull();
        assertThat(root.isPassed()).isTrue();
        assertThat(root.before()).isPresent();
        assertThat(root.children()).hasSize(1);
        var bodyDescriptor = root.children().get(0);
        assertThat(bodyDescriptor.children()).hasSize(5);
        assertThat(root.after()).isPresent();

        assertThat(events)
                .contains("before: parent", "after: parent [PASSED]")
                .contains("before: child-1", "after: child-1 [PASSED]")
                .contains("before: child-2", "after: child-2 [PASSED]")
                .contains("before: child-3", "after: child-3 [PASSED]")
                .contains("before: child-4", "after: child-4 [PASSED]")
                .contains("before: child-5", "after: child-5 [PASSED]");

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

    private static String statusName(final Descriptor descriptor) {
        if (descriptor.isFailed()) {
            return "FAILED";
        }
        if (descriptor.isAborted()) {
            return "ABORTED";
        }
        if (descriptor.isSkipped()) {
            return "SKIPPED";
        }
        return descriptor.isPassed() ? "PASSED" : "RUNNING";
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
            events.add("discovery-completed: " + root.action().displayName());
        }

        @Override
        public void onBeforeExecution(final Descriptor descriptor) {
            events.add("before: " + descriptor.action().displayName());
        }

        @Override
        public void onAfterExecution(final Descriptor descriptor) {
            events.add("after: " + descriptor.action().displayName() + " [" + statusName(descriptor) + "]");
        }

        @Override
        public void onRunCompleted(final Result result) {
            events.add("run-completed");
        }
    }
}
