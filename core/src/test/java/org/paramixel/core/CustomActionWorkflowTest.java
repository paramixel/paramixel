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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.AbstractAction;
import org.paramixel.core.action.Direct;

@DisplayName("Custom action workflows")
class CustomActionWorkflowTest {

    @Test
    @DisplayName("allows test-specific actions to define workflows unlike built-in actions")
    void allowsTestSpecificActionsToDefineWorkflowsUnlikeBuiltInActions() {
        var executions = new ArrayList<String>();
        Action first = recordingAction("first", executions);
        Action repeated = recordingAction("repeated", executions);
        Action last = recordingAction("last", executions);
        Action repeatTwice = new RepeatEachChildAction("repeat-twice", 2, List.of(repeated));
        Action root = new ReverseSequentialAction("reverse-sequential", List.of(first, repeatTwice, last));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(executions).containsExactly("last", "repeated", "repeated", "first");
        assertThat(root.children()).containsExactly(first, repeatTwice, last);
        assertThat(first.parent()).contains(root);
        assertThat(repeatTwice.parent()).contains(root);
        assertThat(repeated.parent()).contains(repeatTwice);
        assertThat(last.parent()).contains(root);
    }

    private static Action recordingAction(String name, List<String> executions) {
        return Direct.of(name, context -> executions.add(context.action().name()));
    }

    private static final class ReverseSequentialAction extends AbstractAction {

        private final List<Action> children;

        private ReverseSequentialAction(String name, List<Action> children) {
            super(name);
            this.children = copyChildren(children);
            this.children.forEach(this::adopt);
        }

        @Override
        public List<Action> children() {
            return children;
        }

        @Override
        protected Result doExecute(Context context, Instant start) throws Throwable {
            List<Action> reversed = new ArrayList<>(children);
            Collections.reverse(reversed);
            for (Action child : reversed) {
                var result = context.execute(child);
                if (result.status() == Result.Status.FAIL) {
                    throw new AssertionError(
                            child.name() + " failed", result.failure().orElse(null));
                }
            }
            return Result.pass(this, durationSince(start));
        }
    }

    private static final class RepeatEachChildAction extends AbstractAction {

        private final int repetitions;
        private final List<Action> children;

        private RepeatEachChildAction(String name, int repetitions, List<Action> children) {
            super(name);
            if (repetitions < 1) {
                throw new IllegalArgumentException("repetitions must be positive");
            }
            this.repetitions = repetitions;
            this.children = copyChildren(children);
            this.children.forEach(this::adopt);
        }

        @Override
        public List<Action> children() {
            return children;
        }

        @Override
        protected Result doExecute(Context context, Instant start) throws Throwable {
            for (int i = 0; i < repetitions; i++) {
                for (Action child : children) {
                    var result = context.execute(child);
                    if (result.status() == Result.Status.FAIL) {
                        throw new AssertionError(
                                child.name() + " failed", result.failure().orElse(null));
                    }
                }
            }
            return Result.pass(this, durationSince(start));
        }
    }

    private static List<Action> copyChildren(List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        List<Action> validated = new ArrayList<>(children.size());
        for (Action child : children) {
            validated.add(Objects.requireNonNull(child, "children must not contain null elements"));
        }
        return Collections.unmodifiableList(validated);
    }
}
