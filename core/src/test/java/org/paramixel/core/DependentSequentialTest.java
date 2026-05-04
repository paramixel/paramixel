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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.DependentSequential;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;

@DisplayName("DependentSequential")
class DependentSequentialTest {

    @Test
    @DisplayName("rejects actions without children")
    void rejectsActionsWithoutGetChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DependentSequential.of("empty", List.of()))
                .withMessage("children must not be empty");
    }

    @Test
    @DisplayName("passes when all children pass")
    void passesWhenAllGetChildrenPass() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action third = Direct.of("third", context -> {});
        Action root = DependentSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(root.getChildren()).hasSize(3);
        assertThat(result.getChildren().get(0).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(1).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(2).getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("stops execution after first child fails")
    void stopsExecutionAfterFirstChildFails() {
        var executions = new ArrayList<String>();
        Action first = Direct.of("first", context -> {
            executions.add("first");
        });
        Action second = Direct.of("second", context -> {
            executions.add("second");
            throw new RuntimeException("fail");
        });
        Action third = Direct.of("third", context -> {
            executions.add("third");
        });
        Action root = DependentSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(executions).containsExactly("first", "second");
        assertThat(root.getChildren()).hasSize(3);
        assertThat(result.getChildren().get(0).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(1).getStatus().isFailure()).isTrue();
        assertThat(result.getChildren().get(2).getStatus().isSkip()).isTrue();
    }

    @Test
    @DisplayName("stops execution after middle child fails")
    void stopsExecutionAfterMiddleChildFails() {
        var executions = new ArrayList<String>();
        Action first = Direct.of("first", context -> {
            executions.add("first");
        });
        Action second = Direct.of("second", context -> {
            executions.add("second");
        });
        Action third = Direct.of("third", context -> {
            executions.add("third");
            throw new RuntimeException("fail");
        });
        Action fourth = Direct.of("fourth", context -> {
            executions.add("fourth");
        });
        Action root = DependentSequential.of("root", List.of(first, second, third, fourth));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(executions).containsExactly("first", "second", "third");
        assertThat(root.getChildren()).hasSize(4);
        assertThat(result.getChildren().get(0).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(1).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(2).getStatus().isFailure()).isTrue();
        assertThat(result.getChildren().get(3).getStatus().isSkip()).isTrue();
    }

    @Test
    @DisplayName("passes when last child fails with no skips")
    void passesWhenLastChildFailsWithNoSkips() {
        var executions = new ArrayList<String>();
        Action first = Direct.of("first", context -> {
            executions.add("first");
        });
        Action second = Direct.of("second", context -> {
            executions.add("second");
        });
        Action third = Direct.of("third", context -> {
            executions.add("third");
            throw new RuntimeException("fail");
        });
        Action root = DependentSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(executions).containsExactly("first", "second", "third");
        assertThat(root.getChildren()).hasSize(3);
        assertThat(result.getChildren().get(0).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(1).getStatus().isPass()).isTrue();
        assertThat(result.getChildren().get(2).getStatus().isFailure()).isTrue();
    }

    @Test
    @DisplayName("fires skipAction callback for skipped children")
    void firesSkipActionCallbackForSkippedGetChildren() {
        var events = new CopyOnWriteArrayList<String>();
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {
            throw new RuntimeException("fail");
        });
        Action third = Direct.of("third", context -> {});
        Action root = DependentSequential.of("root", List.of(first, second, third));

        Listener listener = new Listener() {
            @Override
            public void beforeAction(Result result) {
                events.add("before:" + result.getAction().getName());
            }

            @Override
            public void afterAction(Result result) {
                events.add("after:" + result.getAction().getName() + ":" + result.getStatus());
            }

            @Override
            public void skipAction(Result result) {
                events.add("skip:" + result.getAction().getName());
            }
        };

        Result result = Runner.builder().listener(listener).build().run(root);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(events)
                .containsExactly(
                        "before:root",
                        "before:first",
                        "after:first:PASS",
                        "before:second",
                        "after:second:FAIL",
                        "skip:third",
                        "after:root:FAIL");
    }

    @Test
    @DisplayName("has correct parent references")
    void hasCorrectParentReferences() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action third = Direct.of("third", context -> {});
        Action root = DependentSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(root.getParent()).isEmpty();
        assertThat(root.getChildren()).hasSize(3);

        assertThat(first.getParent()).contains(root);
        assertThat(second.getParent()).contains(root);
        assertThat(third.getParent()).contains(root);
    }

    @Test
    @DisplayName("is not instance of Sequential (separate class hierarchy)")
    void isNotInstanceOfSequential() {
        Action root = DependentSequential.of("root", List.of(Direct.of("child", context -> {})));

        assertThat(root).isNotInstanceOf(org.paramixel.core.action.Sequential.class);
    }

    @Test
    @DisplayName("of(String, Action...) rejects null name")
    void ofVarargsRejectsNullName() {
        assertThatThrownBy(() -> DependentSequential.of(null, Noop.of("child")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, Action...) rejects blank name")
    void ofVarargsRejectsBlankName() {
        assertThatIllegalArgumentException().isThrownBy(() -> DependentSequential.of("", Noop.of("child")));
    }

    @Test
    @DisplayName("of(String, Action...) rejects null array")
    void ofVarargsRejectsNullArray() {
        assertThatThrownBy(() -> DependentSequential.of("test", (Action[]) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("of(String, Action...) rejects empty array")
    void ofVarargsRejectsEmptyArray() {
        assertThatIllegalArgumentException().isThrownBy(() -> DependentSequential.of("test", new Action[0]));
    }

    @Test
    @DisplayName("of(String, List) rejects null list")
    void ofListRejectsNullList() {
        assertThatThrownBy(() -> DependentSequential.of("test", (List<Action>) null))
                .isInstanceOf(NullPointerException.class);
    }
}
