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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.RandomSequential;

@DisplayName("RandomSequential")
class RandomSequentialTest {

    @Test
    @DisplayName("rejects actions without children")
    void rejectsActionsWithoutChildren() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomSequential.of("empty", List.of()))
                .withMessage("sequential action must have at least one child");
    }

    @Test
    @DisplayName("passes when all children pass")
    void passesWhenAllChildrenPass() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action third = Direct.of("third", context -> {});
        Action root = RandomSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children()).hasSize(3);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(1).status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children().get(2).status()).isEqualTo(Result.Status.PASS);
    }

    @Test
    @DisplayName("executes all children regardless of failure")
    void executesAllChildrenRegardlessOfFailure() {
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
        Action root = RandomSequential.of("root", List.of(first, second, third));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(executions).hasSize(3);
        assertThat(result.children()).hasSize(3);
        assertThat(result.children().get(0).status()).isIn(Result.Status.PASS, Result.Status.FAIL);
        assertThat(result.children().get(1).status()).isIn(Result.Status.PASS, Result.Status.FAIL);
        assertThat(result.children().get(2).status()).isIn(Result.Status.PASS, Result.Status.FAIL);
    }

    @Test
    @DisplayName("seeded execution is reproducible")
    void seededExecutionIsReproducible() {
        var firstRun = new ArrayList<String>();
        var secondRun = new ArrayList<String>();

        Action child1 = Direct.of("child1", context -> firstRun.add("child1"));
        Action child2 = Direct.of("child2", context -> firstRun.add("child2"));
        Action child3 = Direct.of("child3", context -> firstRun.add("child3"));
        Action root1 = RandomSequential.of("root", 42L, List.of(child1, child2, child3));
        Runner.builder().build().run(root1);

        Action child1b = Direct.of("child1", context -> secondRun.add("child1"));
        Action child2b = Direct.of("child2", context -> secondRun.add("child2"));
        Action child3b = Direct.of("child3", context -> secondRun.add("child3"));
        Action root2 = RandomSequential.of("root", 42L, List.of(child1b, child2b, child3b));
        Runner.builder().build().run(root2);

        assertThat(firstRun).isEqualTo(secondRun);
    }

    @Test
    @DisplayName("different seeds produce different orders")
    void differentSeedsProduceDifferentOrders() {
        var firstRun = new ArrayList<String>();
        var secondRun = new ArrayList<String>();

        Action child1 = Direct.of("child1", context -> firstRun.add("child1"));
        Action child2 = Direct.of("child2", context -> firstRun.add("child2"));
        Action child3 = Direct.of("child3", context -> firstRun.add("child3"));
        Action root1 = RandomSequential.of("root", 1L, List.of(child1, child2, child3));
        Runner.builder().build().run(root1);

        Action child1b = Direct.of("child1", context -> secondRun.add("child1"));
        Action child2b = Direct.of("child2", context -> secondRun.add("child2"));
        Action child3b = Direct.of("child3", context -> secondRun.add("child3"));
        Action root2 = RandomSequential.of("root", 999L, List.of(child1b, child2b, child3b));
        Runner.builder().build().run(root2);

        assertThat(firstRun).isNotEqualTo(secondRun);
    }

    @Test
    @DisplayName("unseeded execution does not crash")
    void unseededExecutionDoesNotCrash() {
        Action child1 = Direct.of("child1", context -> {});
        Action child2 = Direct.of("child2", context -> {});
        Action child3 = Direct.of("child3", context -> {});
        Action root = RandomSequential.of("root", List.of(child1, child2, child3));

        Result result = Runner.builder().build().run(root);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.children()).hasSize(3);
    }

    @Test
    @DisplayName("has correct parent references")
    void hasCorrectParentReferences() {
        Action first = Direct.of("first", context -> {});
        Action second = Direct.of("second", context -> {});
        Action third = Direct.of("third", context -> {});
        Action root = RandomSequential.of("root", List.of(first, second, third));

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
        Action root = RandomSequential.of("root", List.of(Direct.of("child", context -> {})));

        assertThat(root).isInstanceOf(org.paramixel.core.action.Sequential.class);
    }

    @Test
    @DisplayName("returns empty seed when unseeded")
    void returnsEmptySeedWhenUnseeded() {
        Action root = RandomSequential.of("root", List.of(Direct.of("child", context -> {})));

        org.paramixel.core.action.RandomSequential random = (org.paramixel.core.action.RandomSequential) root;
        assertThat(random.seed()).isEmpty();
    }

    @Test
    @DisplayName("returns seed when seeded")
    void returnsSeedWhenSeeded() {
        Action root = RandomSequential.of("root", 42L, List.of(Direct.of("child", context -> {})));

        org.paramixel.core.action.RandomSequential random = (org.paramixel.core.action.RandomSequential) root;
        assertThat(random.seed()).hasValue(42L);
    }
}
