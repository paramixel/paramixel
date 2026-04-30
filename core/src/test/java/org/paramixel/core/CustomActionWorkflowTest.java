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
import org.paramixel.core.internal.DefaultContext;
import org.paramixel.core.internal.Results;

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

        Runner runner = Runner.builder().build();
        runner.run(root);
        Result result = root.getResult();

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(executions).containsExactly("last", "repeated", "repeated", "first");
        assertThat(root.getChildren()).containsExactly(first, repeatTwice, last);
        assertThat(first.getParent()).contains(root);
        assertThat(repeatTwice.getParent()).contains(root);
        assertThat(repeated.getParent()).contains(repeatTwice);
        assertThat(last.getParent()).contains(root);
    }

    private static Action recordingAction(String name, List<String> executions) {
        return Direct.of(name, context -> executions.add(name));
    }

    private static final class ReverseSequentialAction extends AbstractAction {

        private final List<Action> children;

        private ReverseSequentialAction(String name, List<Action> children) {
            super(name);
            this.children = copyChildren(children);
            this.children.forEach(this::addChild);
        }

        @Override
        public List<Action> getChildren() {
            return children;
        }

        @Override
        public void execute(Context context) {
            Objects.requireNonNull(context, "context must not be null");
            this.result = Results.staged();
            context.getListener().beforeAction(context, this);
            Instant start = Instant.now();

            List<Action> reversed = new ArrayList<>(children);
            Collections.reverse(reversed);
            DefaultContext defaultContext = (DefaultContext) context;
            for (Action child : reversed) {
                child.execute(new DefaultContext(defaultContext));
                if (child.getResult().getStatus().isFailure()) {
                    this.result = Results.fail(
                            durationSince(start),
                            child.getResult().getStatus().getThrowable().orElse(null));
                    context.getListener().afterAction(context, this, this.result);
                    return;
                }
            }
            this.result = Results.pass(durationSince(start));
            context.getListener().afterAction(context, this, this.result);
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
            this.children.forEach(this::addChild);
        }

        @Override
        public List<Action> getChildren() {
            return children;
        }

        @Override
        public void execute(Context context) {
            Objects.requireNonNull(context, "context must not be null");
            this.result = Results.staged();
            context.getListener().beforeAction(context, this);
            Instant start = Instant.now();

            DefaultContext defaultContext = (DefaultContext) context;
            for (int i = 0; i < repetitions; i++) {
                for (Action child : children) {
                    child.execute(new DefaultContext(defaultContext));
                    if (child.getResult().getStatus().isFailure()) {
                        this.result = Results.fail(
                                durationSince(start),
                                child.getResult().getStatus().getThrowable().orElse(null));
                        context.getListener().afterAction(context, this, this.result);
                        return;
                    }
                }
            }
            this.result = Results.pass(durationSince(start));
            context.getListener().afterAction(context, this, this.result);
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
