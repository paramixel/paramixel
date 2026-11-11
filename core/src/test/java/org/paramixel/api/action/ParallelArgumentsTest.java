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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Parallel arguments")
class ParallelArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Parallel.parallel(null, "t0")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Parallel.parallel(" ", "t1")).isInstanceOf(IllegalArgumentException.class);
        var parallel = Parallel.parallel("empty", "t2").build();
        assertThat(parallel.children()).isEmpty();
    }

    @Test
    @DisplayName("child(Action) rejects null builder")
    void childBuilderRejectsNull() {
        var builder = Parallel.parallel("parallel", "t3");
        assertThatThrownBy(() -> builder.child((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Action) adds child from builder")
    void childBuilderAddsChild() {
        var parallel = Parallel.parallel("parallel", "t4")
                .child(Step.step("step", s -> {}))
                .build();
        assertThat(parallel.children()).hasSize(1);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var parallel = Parallel.parallel("parallel", "t5")
                .child(Step.step("first", s -> {}))
                .child(Step.step("second", s -> {}))
                .child(Step.step("third", s -> {}))
                .build();
        assertThat(parallel.children()).hasSize(3);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("first");
        assertThat(parallel.children().get(1).displayName()).isEqualTo("second");
        assertThat(parallel.children().get(2).displayName()).isEqualTo("third");
    }

    @Test
    @DisplayName("pool() returns the pool name")
    void poolReturnsPoolName() {
        var parallel = Parallel.parallel("parallel", "docker")
                .child(Step.step("step", s -> {}))
                .build();

        assertThat(parallel.pool()).isEqualTo("docker");
    }

    @Test
    @DisplayName("pool name accepts letters, digits, underscore and hyphen after a leading letter")
    void poolNameAcceptsValidNames() {
        for (var name : new String[] {"a", "docker", "db-primary", "env_1", "Env2", "root"}) {
            assertThat(Parallel.parallel("p", name)
                            .child(Step.step("step", s -> {}))
                            .build()
                            .pool())
                    .isEqualTo(name);
        }
    }

    @Test
    @DisplayName("pool name rejects null")
    void poolNameRejectsNull() {
        assertThatThrownBy(() -> Parallel.parallel("p", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("pool name rejects blank, dotted, hyphen-led and digit-led names")
    void poolNameRejectsInvalidNames() {
        for (var name : new String[] {"", " ", "a.b", "-a", "1a", "a b", "_a"}) {
            assertThatThrownBy(() -> Parallel.parallel("p", name))
                    .as("pool name '%s'", name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Parallel.parallel("parallel", "t6").child(Step.step("test", s -> {}));
        var first = builder.build();
        builder.child(Step.step("other", s -> {}));
        var second = builder.build();

        assertThat(first.children()).extracting(Action::displayName).containsExactly("test");
        assertThat(second.children()).extracting(Action::displayName).containsExactly("test", "other");
        assertThat(second.pool()).isEqualTo(first.pool());
    }

    @Test
    @DisplayName("Parallel does not have dependent/independent")
    void parallelHasNoDependentIndependent() {
        var parallel = Parallel.parallel("parallel", "t7")
                .child(Step.step("test", s -> {}))
                .build();
        assertThat(parallel.getClass().getDeclaredFields())
                .anyMatch(f -> !f.getName().equals("dependent"));
    }

    @Test
    @DisplayName("default shuffleSeed is empty")
    void defaultNotShuffled() {
        var parallel =
                Parallel.parallel("p", "t8").child(Step.step("a", s -> {})).build();
        assertThat(parallel.shuffleSeed()).isEmpty();
    }

    @Test
    @DisplayName("shuffle() sets a non-zero shuffleSeed")
    void shuffleSetsShuffledAndSeed() {
        var parallel = Parallel.parallel("p", "t9")
                .shuffle()
                .child(Step.step("a", s -> {}))
                .child(Step.step("b", s -> {}))
                .build();
        assertThat(parallel.shuffleSeed()).isPresent();
        assertThat(parallel.shuffleSeed().orElseThrow()).isNotZero();
    }

    @Test
    @DisplayName("shuffle(long seed) stores explicit seed")
    void shuffleWithSeedStoresSeed() {
        var parallel = Parallel.parallel("p", "t10")
                .shuffle(42L)
                .child(Step.step("a", s -> {}))
                .child(Step.step("b", s -> {}))
                .build();
        assertThat(parallel.shuffleSeed()).isPresent();
        assertThat(parallel.shuffleSeed().orElseThrow()).isEqualTo(42L);
    }

    @Test
    @DisplayName("shuffle with known seed produces predictable order")
    void shuffleWithKnownSeedProducesPredictableOrder() {
        var parallel = Parallel.parallel("p", "t11")
                .shuffle(42L)
                .child(Step.step("a", s -> {}))
                .child(Step.step("b", s -> {}))
                .child(Step.step("c", s -> {}))
                .build();
        var names = parallel.children().stream().map(Action::displayName).toList();
        var parallel2 = Parallel.parallel("p", "t0")
                .shuffle(42L)
                .child(Step.step("a", s -> {}))
                .child(Step.step("b", s -> {}))
                .child(Step.step("c", s -> {}))
                .build();
        assertThat(parallel2.children().stream().map(Action::displayName).toList())
                .isEqualTo(names);
    }

    @Test
    @DisplayName("shuffle with 0 children still reports shuffled")
    void shuffleWithZeroChildrenStillShuffled() {
        var parallel = Parallel.parallel("p", "t1").shuffle().build();
        assertThat(parallel.shuffleSeed()).isPresent();
        assertThat(parallel.shuffleSeed().orElseThrow()).isNotZero();
        assertThat(parallel.children()).isEmpty();
    }

    @Test
    @DisplayName("shuffle with 1 child is no-op but still shuffled")
    void shuffleSingleChildStillShuffled() {
        var parallel = Parallel.parallel("p", "t2")
                .shuffle()
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(parallel.shuffleSeed()).isPresent();
        assertThat(parallel.shuffleSeed().orElseThrow()).isNotZero();
        assertThat(parallel.children()).hasSize(1);
    }

    @Test
    @DisplayName("shuffle preserves immutability/reusability of builder")
    void shufflePreservesImmutability() {
        var builder = Parallel.parallel("p", "t3").shuffle(42L).child(Step.step("a", s -> {}));
        var first = builder.build();
        builder.child(Step.step("b", s -> {}));
        var second = builder.build();
        assertThat(first.children()).hasSize(1);
        assertThat(second.children()).hasSize(2);
        assertThat(first.shuffleSeed()).isPresent();
        assertThat(second.shuffleSeed()).isPresent();
    }

    @Test
    @DisplayName("last shuffle call wins")
    void lastShuffleCallWins() {
        var parallel = Parallel.parallel("p", "t4")
                .shuffle(42L)
                .shuffle()
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(parallel.shuffleSeed().orElseThrow()).isNotEqualTo(42L);
        var parallel2 = Parallel.parallel("p", "t5")
                .shuffle()
                .shuffle(42L)
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(parallel2.shuffleSeed().orElseThrow()).isEqualTo(42L);
    }

    @Test
    @DisplayName("shuffle with seed 0 is valid")
    void shuffleWithSeedZeroIsValid() {
        var parallel = Parallel.parallel("p", "t6")
                .shuffle(0L)
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(parallel.shuffleSeed()).isPresent();
        assertThat(parallel.shuffleSeed().orElseThrow()).isZero();
    }

    @Test
    @DisplayName("shuffle with negative seed is valid")
    void shuffleWithNegativeSeedIsValid() {
        var parallel = Parallel.parallel("p", "t7")
                .shuffle(-1L)
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(parallel.shuffleSeed()).isPresent();
        assertThat(parallel.shuffleSeed().orElseThrow()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("child(Builder) builds and adds child action")
    void childBuilderBuildsAndAddsChildAction() {
        var parallel = Parallel.parallel("p", "t8")
                .child(Step.step("a", s -> {}))
                .child(Sequential.sequential("nested").child(Step.step("b", s -> {})))
                .build();
        assertThat(parallel.children()).hasSize(2);
        assertThat(parallel.children().get(1).displayName()).isEqualTo("nested");
    }

    @Test
    @DisplayName("child(Builder) rejects null builder")
    void childBuilderOverloadRejectsNull() {
        var builder = Parallel.parallel("p", "t9");
        assertThatThrownBy(() -> builder.child((org.paramixel.api.action.ActionBuilder) null))
                .isInstanceOf(NullPointerException.class);
    }
}
