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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Noop;

@DisplayName("Action")
class ActionTest {

    @Test
    @DisplayName("creates noop actions that complete without doing work")
    void createsNoopActionsThatCompleteWithoutDoingWork() {
        Action action = Noop.of("noop");

        Result result = Runner.builder().build().run(action);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("runs custom action implementation")
    void runsCustomActionImplementation() {
        CustomAction action = new CustomAction();

        Result result = Runner.builder().build().run(action);

        assertThat(action.executed()).isTrue();
        assertThat(action.skipped()).isFalse();
        assertThat(result.getAction()).isSameAs(action);
        assertThat(result.getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("runs custom composite action implementation with children")
    void runsCustomCompositeActionImplementationWithChildren() {
        CustomAction first = new CustomAction("first-child-id", "first child");
        CustomAction second = new CustomAction("second-child-id", "second child");
        CompositeCustomAction action = new CompositeCustomAction(first, second);

        Result result = Runner.builder().build().run(action);

        assertThat(action.executed()).isTrue();
        assertThat(first.executed()).isTrue();
        assertThat(second.executed()).isTrue();
        assertThat(result.getAction()).isSameAs(action);
        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getChildren()).extracting(Result::getAction).containsExactly(first, second);
    }

    @Test
    @DisplayName("composite action returns unmodifiable children")
    void compositeActionReturnsUnmodifiableChildren() {
        Action first = Noop.of("first");
        Action second = Noop.of("second");
        CompositeAction root = Container.builder("root")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build())
                .child(first)
                .child(second)
                .build();

        assertThat(root.getChildren()).containsExactly(first, second);
        assertThatThrownBy(() -> root.getChildren().remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class CustomAction implements Action {

        private final String id;
        private final String name;
        private final AtomicBoolean executed = new AtomicBoolean();
        private final AtomicBoolean skipped = new AtomicBoolean();

        private CustomAction() {
            this("custom-action-id", "custom action");
        }

        private CustomAction(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Result run(Context context) {
            executed.set(true);
            return Result.pass(this);
        }

        @Override
        public Result skip(Context context) {
            skipped.set(true);
            return Result.skip(this);
        }

        boolean executed() {
            return executed.get();
        }

        boolean skipped() {
            return skipped.get();
        }
    }

    private static final class CompositeCustomAction implements CompositeAction {

        private final List<Action> children;
        private final AtomicBoolean executed = new AtomicBoolean();

        private CompositeCustomAction(Action... children) {
            this.children = List.of(children);
        }

        @Override
        public String getId() {
            return "custom-composite-action-id";
        }

        @Override
        public String getName() {
            return "custom composite action";
        }

        @Override
        public List<Action> getChildren() {
            return children;
        }

        @Override
        public Result run(Context context) {
            executed.set(true);
            Result.Builder result = Result.builder(this).status(Status.pass());
            for (Action child : children) {
                result.child(child.run(context));
            }
            return result.build();
        }

        @Override
        public Result skip(Context context) {
            Result.Builder result = Result.builder(this).status(Status.skip());
            for (Action child : children) {
                result.child(child.skip(context));
            }
            return result.build();
        }

        boolean executed() {
            return executed.get();
        }
    }
}
