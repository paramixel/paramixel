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
@SuppressWarnings("removal")
class ParallelArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Parallel.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Parallel.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        var parallel = Parallel.builder("empty").build();
        assertThat(parallel.children()).isEmpty();
    }

    @Test
    @DisplayName("child(Action) rejects null builder")
    void childBuilderRejectsNull() {
        var builder = Parallel.builder("parallel");
        assertThatThrownBy(() -> builder.child((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Action) adds child from builder")
    void childBuilderAddsChild() {
        var parallel =
                Parallel.builder("parallel").child(Step.of("step", s -> {})).build();
        assertThat(parallel.children()).hasSize(1);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var parallel = Parallel.builder("parallel")
                .child(Step.of("first", s -> {}))
                .child(Step.of("second", s -> {}))
                .child(Step.of("third", s -> {}))
                .build();
        assertThat(parallel.children()).hasSize(3);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("first");
        assertThat(parallel.children().get(1).displayName()).isEqualTo("second");
        assertThat(parallel.children().get(2).displayName()).isEqualTo("third");
    }

    @Test
    @DisplayName("parallelism() rejects zero")
    void parallelismRejectsZero() {
        var builder = Parallel.builder("parallel");
        assertThatThrownBy(() -> builder.parallelism(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parallelism() rejects negative")
    void parallelismRejectsNegative() {
        var builder = Parallel.builder("parallel");
        assertThatThrownBy(() -> builder.parallelism(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parallelism() sets parallelism")
    void parallelismSetsParallelism() {
        var parallel = Parallel.builder("parallel")
                .parallelism(4)
                .child(Step.of("step", s -> {}))
                .build();
        assertThat(parallel.parallelism()).isEqualTo(4);
    }

    @Test
    @DisplayName("default parallelism is Integer.MAX_VALUE")
    void defaultParallelismIsMaxValue() {
        var parallel =
                Parallel.builder("parallel").child(Step.of("step", s -> {})).build();
        assertThat(parallel.parallelism()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Parallel.builder("parallel");
        builder.child(Step.of("test", s -> {}));
        var first = builder.build();
        builder.child(Step.of("other", s -> {}));
        builder.parallelism(2);
        var second = builder.build();

        assertThat(first.children()).extracting(Action::displayName).containsExactly("test");
        assertThat(first.parallelism()).isEqualTo(Integer.MAX_VALUE);
        assertThat(second.children()).extracting(Action::displayName).containsExactly("test", "other");
        assertThat(second.parallelism()).isEqualTo(2);
    }

    @Test
    @DisplayName("Parallel does not have dependent/independent")
    void parallelHasNoDependentIndependent() {
        var parallel =
                Parallel.builder("parallel").child(Step.of("test", s -> {})).build();
        assertThat(parallel.getClass().getDeclaredFields())
                .anyMatch(f -> !f.getName().equals("dependent"));
    }

    @Test
    @DisplayName("default isShuffled is false, seed is 0")
    void defaultNotShuffled() {
        var parallel = Parallel.builder("p").child(Step.of("a", s -> {})).build();
        assertThat(parallel.isShuffled()).isFalse();
        assertThat(parallel.seed()).isZero();
    }

    @Test
    @DisplayName("shuffle() sets isShuffled and non-zero seed")
    void shuffleSetsShuffledAndSeed() {
        var parallel = Parallel.builder("p")
                .shuffle()
                .child(Step.of("a", s -> {}))
                .child(Step.of("b", s -> {}))
                .build();
        assertThat(parallel.isShuffled()).isTrue();
        assertThat(parallel.seed()).isNotZero();
    }

    @Test
    @DisplayName("shuffle(long seed) stores explicit seed")
    void shuffleWithSeedStoresSeed() {
        var parallel = Parallel.builder("p")
                .shuffle(42L)
                .child(Step.of("a", s -> {}))
                .child(Step.of("b", s -> {}))
                .build();
        assertThat(parallel.isShuffled()).isTrue();
        assertThat(parallel.seed()).isEqualTo(42L);
    }

    @Test
    @DisplayName("shuffle with known seed produces predictable order")
    void shuffleWithKnownSeedProducesPredictableOrder() {
        var parallel = Parallel.builder("p")
                .shuffle(42L)
                .child(Step.of("a", s -> {}))
                .child(Step.of("b", s -> {}))
                .child(Step.of("c", s -> {}))
                .build();
        var names = parallel.children().stream().map(Action::displayName).toList();
        var parallel2 = Parallel.builder("p")
                .shuffle(42L)
                .child(Step.of("a", s -> {}))
                .child(Step.of("b", s -> {}))
                .child(Step.of("c", s -> {}))
                .build();
        assertThat(parallel2.children().stream().map(Action::displayName).toList())
                .isEqualTo(names);
    }

    @Test
    @DisplayName("shuffle with 0 children still reports shuffled")
    void shuffleWithZeroChildrenStillShuffled() {
        var parallel = Parallel.builder("p").shuffle().build();
        assertThat(parallel.isShuffled()).isTrue();
        assertThat(parallel.seed()).isNotZero();
        assertThat(parallel.children()).isEmpty();
    }

    @Test
    @DisplayName("shuffle with 1 child is no-op but still shuffled")
    void shuffleSingleChildStillShuffled() {
        var parallel =
                Parallel.builder("p").shuffle().child(Step.of("a", s -> {})).build();
        assertThat(parallel.isShuffled()).isTrue();
        assertThat(parallel.seed()).isNotZero();
        assertThat(parallel.children()).hasSize(1);
    }

    @Test
    @DisplayName("shuffle preserves immutability/reusability of builder")
    void shufflePreservesImmutability() {
        var builder = Parallel.builder("p").shuffle(42L).child(Step.of("a", s -> {}));
        var first = builder.build();
        builder.child(Step.of("b", s -> {}));
        var second = builder.build();
        assertThat(first.children()).hasSize(1);
        assertThat(second.children()).hasSize(2);
        assertThat(first.isShuffled()).isTrue();
        assertThat(second.isShuffled()).isTrue();
    }

    @Test
    @DisplayName("last shuffle call wins")
    void lastShuffleCallWins() {
        var parallel = Parallel.builder("p")
                .shuffle(42L)
                .shuffle()
                .child(Step.of("a", s -> {}))
                .build();
        assertThat(parallel.seed()).isNotEqualTo(42L);
        var parallel2 = Parallel.builder("p")
                .shuffle()
                .shuffle(42L)
                .child(Step.of("a", s -> {}))
                .build();
        assertThat(parallel2.seed()).isEqualTo(42L);
    }

    @Test
    @DisplayName("shuffle with seed 0 is valid")
    void shuffleWithSeedZeroIsValid() {
        var parallel =
                Parallel.builder("p").shuffle(0L).child(Step.of("a", s -> {})).build();
        assertThat(parallel.isShuffled()).isTrue();
        assertThat(parallel.seed()).isZero();
    }

    @Test
    @DisplayName("shuffle with negative seed is valid")
    void shuffleWithNegativeSeedIsValid() {
        var parallel =
                Parallel.builder("p").shuffle(-1L).child(Step.of("a", s -> {})).build();
        assertThat(parallel.isShuffled()).isTrue();
        assertThat(parallel.seed()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("child(Builder) builds and adds child action")
    void childBuilderBuildsAndAddsChildAction() {
        var parallel = Parallel.builder("p")
                .child(Step.of("a", s -> {}))
                .child(Sequence.builder("nested").child(Step.of("b", s -> {})))
                .build();
        assertThat(parallel.children()).hasSize(2);
        assertThat(parallel.children().get(1).displayName()).isEqualTo("nested");
    }

    @Test
    @DisplayName("child(Builder) rejects null builder")
    void childBuilderOverloadRejectsNull() {
        var builder = Parallel.builder("p");
        assertThatThrownBy(() -> builder.child((org.paramixel.api.action.Builder) null))
                .isInstanceOf(NullPointerException.class);
    }
}
