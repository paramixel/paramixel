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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.StrictSequential;

@DisplayName("StrictSequential")
class StrictSequentialTest {

    @Test
    @DisplayName("rejects actions without children")
    void rejectsActionsWithoutChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StrictSequential.of("empty", List.of()))
                .withMessage("sequential action must have at least one child");
    }

    @Test
    @DisplayName("passes when all children pass")
    void passesWhenAllChildrenPass() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action third = Direct.of("third", context -> {});
        Action root = StrictSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children()).hasSize(3);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(1).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(2).status()).isEqualTo(Result.Status.PASS);
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
        Action root = StrictSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(executions).containsExactly("first", "second");
        assertThat(result.children()).hasSize(3);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(1).status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.children().get(2).status()).isEqualTo(Result.Status.SKIP);
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
        Action root = StrictSequential.of("root", List.of(first, second, third, fourth));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(executions).containsExactly("first", "second", "third");
        assertThat(result.children()).hasSize(4);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(1).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(2).status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.children().get(3).status()).isEqualTo(Result.Status.SKIP);
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
        Action root = StrictSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(executions).containsExactly("first", "second", "third");
        assertThat(result.children()).hasSize(3);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(1).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(2).status()).isEqualTo(Result.Status.FAIL);
    }

    @Test
    @DisplayName("fires before and after callbacks for skipped children")
    void firesBeforeAndAfterCallbacksForSkippedChildren() {
        var events = new CopyOnWriteArrayList<String>();
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {
            throw new RuntimeException("fail");
        });
        Action third = Direct.of("third", context -> {});
        Action root = StrictSequential.of("root", List.of(first, second, third));

        Listener listener = new Listener() {
            @Override
            public void beforeAction(Context context, Action action) {
                events.add("before:" + action.name());
            }

            @Override
            public void afterAction(Context context, Action action, Result result) {
                events.add("after:" + action.name() + ":" + result.status());
            }
        };

        Result result = Runner.builder().listener(listener).build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(events)
                .containsExactly(
                        "before:root",
                        "before:first",
                        "after:first:PASS",
                        "before:second",
                        "after:second:FAIL",
                        "before:third",
                        "after:third:SKIP",
                        "after:root:FAIL");
    }

    @Test
    @DisplayName("has correct parent references")
    void hasCorrectParentReferences() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action third = Direct.of("third", context -> {});
        Action root = StrictSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.parent()).isEmpty();
        assertThat(result.children()).hasSize(3);

        Result firstResult = result.children().get(0);
        Result secondResult = result.children().get(1);
        Result thirdResult = result.children().get(2);

        assertThat(firstResult.parent()).contains(result);
        assertThat(secondResult.parent()).contains(result);
        assertThat(thirdResult.parent()).contains(result);
    }

    @Test
    @DisplayName("is instance of Sequential")
    void isInstanceOfSequential() {
        Action root = StrictSequential.of("root", List.of(Direct.of("child", context -> {})));

        assertThat(root).isInstanceOf(org.paramixel.core.action.Sequential.class);
    }
}
