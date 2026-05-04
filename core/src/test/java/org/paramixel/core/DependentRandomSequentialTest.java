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
import org.paramixel.core.action.DependentRandomSequential;
import org.paramixel.core.action.Direct;

@DisplayName("DependentRandomSequential")
class DependentRandomSequentialTest {

    @Test
    @DisplayName("rejects actions without children")
    void rejectsActionsWithoutGetChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DependentRandomSequential.of("empty", List.of()))
                .withMessage("children must not be empty");
    }

    @Test
    @DisplayName("passes when all children pass")
    void passesWhenAllGetChildrenPass() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action third = Direct.of("third", context -> {});
        Action root = DependentRandomSequential.of("root", List.of(first, second, third));

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
        Action root = DependentRandomSequential.of("root", 0L, List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(executions).hasSize(2);
        assertThat(root.getChildren()).hasSize(3);
        long passCount = result.getChildren().stream()
                .filter(r -> r.getStatus().isPass())
                .count();
        long failCount = result.getChildren().stream()
                .filter(r -> r.getStatus().isFailure())
                .count();
        long skipCount = result.getChildren().stream()
                .filter(r -> r.getStatus().isSkip())
                .count();
        assertThat(passCount).isEqualTo(1);
        assertThat(failCount).isEqualTo(1);
        assertThat(skipCount).isEqualTo(1);
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
        Action root = DependentRandomSequential.of("root", 42L, List.of(first, second, third, fourth));

        Result result = Runner.builder().build().run(root);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(executions).hasSize(4);
        assertThat(root.getChildren()).hasSize(4);
        long passCount = result.getChildren().stream()
                .filter(r -> r.getStatus().isPass())
                .count();
        long failCount = result.getChildren().stream()
                .filter(r -> r.getStatus().isFailure())
                .count();
        long skipCount = result.getChildren().stream()
                .filter(r -> r.getStatus().isSkip())
                .count();
        assertThat(passCount).isEqualTo(3);
        assertThat(failCount).isEqualTo(1);
        assertThat(skipCount).isEqualTo(0);
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
        Action root = DependentRandomSequential.of("root", 0L, List.of(first, second, third));

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
        long beforeCount = events.stream().filter(e -> e.startsWith("before:")).count();
        long afterCount = events.stream().filter(e -> e.startsWith("after:")).count();
        long skipCount = events.stream().filter(e -> e.startsWith("skip:")).count();
        assertThat(beforeCount).isEqualTo(3);
        assertThat(afterCount).isEqualTo(3);
        assertThat(skipCount).isEqualTo(1);
    }

    @Test
    @DisplayName("seeded execution is reproducible")
    void seededExecutionIsReproducible() {
        var firstRun = new ArrayList<String>();
        var secondRun = new ArrayList<String>();

        Action child1 = Direct.of("child1", context -> firstRun.add("child1"));
        Action child2 = Direct.of("child2", context -> firstRun.add("child2"));
        Action child3 = Direct.of("child3", context -> firstRun.add("child3"));
        Action root1 = DependentRandomSequential.of("root", 42L, List.of(child1, child2, child3));
        Runner.builder().build().run(root1);

        Action child1b = Direct.of("child1", context -> secondRun.add("child1"));
        Action child2b = Direct.of("child2", context -> secondRun.add("child2"));
        Action child3b = Direct.of("child3", context -> secondRun.add("child3"));
        Action root2 = DependentRandomSequential.of("root", 42L, List.of(child1b, child2b, child3b));
        Runner.builder().build().run(root2);

        assertThat(firstRun).isEqualTo(secondRun);
    }

    @Test
    @DisplayName("has correct parent references")
    void hasCorrectParentReferences() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action third = Direct.of("third", context -> {});
        Action root = DependentRandomSequential.of("root", List.of(first, second, third));

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
        Action root = DependentRandomSequential.of("root", List.of(Direct.of("child", context -> {})));

        assertThat(root).isNotInstanceOf(org.paramixel.core.action.Sequential.class);
    }

    @Test
    @DisplayName("is not instance of RandomSequential (separate class)")
    void isNotInstanceOfRandomSequential() {
        Action root = DependentRandomSequential.of("root", List.of(Direct.of("child", context -> {})));

        assertThat(root).isNotInstanceOf(org.paramixel.core.action.RandomSequential.class);
    }

    @Test
    @DisplayName("returns empty seed when unseeded")
    void returnsEmptySeedWhenUnseeded() {
        DependentRandomSequential root =
                DependentRandomSequential.of("root", List.of(Direct.of("child", context -> {})));

        assertThat(root.seed()).isEmpty();
    }

    @Test
    @DisplayName("returns seed when seeded")
    void returnsSeedWhenSeeded() {
        DependentRandomSequential root =
                DependentRandomSequential.of("root", 42L, List.of(Direct.of("child", context -> {})));

        assertThat(root.seed()).hasValue(42L);
    }
}
