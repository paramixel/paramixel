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

@DisplayName("Sequence arguments")
@SuppressWarnings("removal")
class SequenceArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Sequence.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Sequence.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        var seq = Sequence.builder("empty").build();
        assertThat(seq.children()).isEmpty();
    }

    @Test
    @DisplayName("child(Action) rejects null builder")
    void childBuilderRejectsNull() {
        var builder = Sequence.builder("seq");
        assertThatThrownBy(() -> builder.child((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Action) adds child from builder")
    void childBuilderAddsChild() {
        var seq = Sequence.builder("seq").child(Step.of("step", s -> {})).build();
        assertThat(seq.children()).hasSize(1);
        assertThat(seq.children().get(0).displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var seq = Sequence.builder("seq")
                .child(Step.of("first", s -> {}))
                .child(Step.of("second", s -> {}))
                .child(Step.of("third", s -> {}))
                .build();
        assertThat(seq.children()).hasSize(3);
        assertThat(seq.children().get(0).displayName()).isEqualTo("first");
        assertThat(seq.children().get(1).displayName()).isEqualTo("second");
        assertThat(seq.children().get(2).displayName()).isEqualTo("third");
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Sequence.builder("seq");
        builder.child(Step.of("test", s -> {}));
        var first = builder.build();
        builder.child(Step.of("other", s -> {}));
        builder.independent();
        var second = builder.build();

        assertThat(first.children()).extracting(Action::displayName).containsExactly("test");
        assertThat(first.isDependent()).isTrue();
        assertThat(second.children()).extracting(Action::displayName).containsExactly("test", "other");
        assertThat(second.isIndependent()).isTrue();
    }

    @Test
    @DisplayName("default is dependent")
    void defaultIsDependent() {
        var seq = Sequence.builder("seq").child(Step.of("test", s -> {})).build();
        assertThat(seq.isDependent()).isTrue();
        assertThat(seq.isIndependent()).isFalse();
    }

    @Test
    @DisplayName("independent() configures as independent")
    void independentConfiguresAsIndependent() {
        var seq = Sequence.builder("seq")
                .independent()
                .child(Step.of("test", s -> {}))
                .build();
        assertThat(seq.isIndependent()).isTrue();
        assertThat(seq.isDependent()).isFalse();
    }

    @Test
    @DisplayName("dependent() after independent() restores dependent")
    void dependentAfterIndependentRestoresDependent() {
        var seq = Sequence.builder("seq")
                .independent()
                .dependent()
                .child(Step.of("test", s -> {}))
                .build();
        assertThat(seq.isDependent()).isTrue();
    }

    @Test
    @DisplayName("default isShuffled is false, seed is 0")
    void defaultNotShuffled() {
        var seq = Sequence.builder("seq").child(Step.of("a", s -> {})).build();
        assertThat(seq.isShuffled()).isFalse();
        assertThat(seq.seed()).isZero();
    }

    @Test
    @DisplayName("shuffle() sets isShuffled and non-zero seed")
    void shuffleSetsShuffledAndSeed() {
        var seq = Sequence.builder("seq")
                .shuffle()
                .child(Step.of("a", s -> {}))
                .child(Step.of("b", s -> {}))
                .build();
        assertThat(seq.isShuffled()).isTrue();
        assertThat(seq.seed()).isNotZero();
    }

    @Test
    @DisplayName("shuffle(long seed) stores explicit seed")
    void shuffleWithSeedStoresSeed() {
        var seq = Sequence.builder("seq")
                .shuffle(42L)
                .child(Step.of("a", s -> {}))
                .child(Step.of("b", s -> {}))
                .build();
        assertThat(seq.isShuffled()).isTrue();
        assertThat(seq.seed()).isEqualTo(42L);
    }

    @Test
    @DisplayName("shuffle with known seed produces predictable order")
    void shuffleWithKnownSeedProducesPredictableOrder() {
        var seq = Sequence.builder("seq")
                .shuffle(42L)
                .child(Step.of("a", s -> {}))
                .child(Step.of("b", s -> {}))
                .child(Step.of("c", s -> {}))
                .build();
        var names = seq.children().stream().map(Action::displayName).toList();
        var seq2 = Sequence.builder("seq")
                .shuffle(42L)
                .child(Step.of("a", s -> {}))
                .child(Step.of("b", s -> {}))
                .child(Step.of("c", s -> {}))
                .build();
        assertThat(seq2.children().stream().map(Action::displayName).toList()).isEqualTo(names);
    }

    @Test
    @DisplayName("shuffle with 0 children still reports shuffled")
    void shuffleWithZeroChildrenStillShuffled() {
        var seq = Sequence.builder("seq").shuffle().build();
        assertThat(seq.isShuffled()).isTrue();
        assertThat(seq.seed()).isNotZero();
        assertThat(seq.children()).isEmpty();
    }

    @Test
    @DisplayName("shuffle with 1 child is no-op but still shuffled")
    void shuffleSingleChildStillShuffled() {
        var seq = Sequence.builder("seq").shuffle().child(Step.of("a", s -> {})).build();
        assertThat(seq.isShuffled()).isTrue();
        assertThat(seq.seed()).isNotZero();
        assertThat(seq.children()).hasSize(1);
    }

    @Test
    @DisplayName("shuffle preserves immutability/reusability of builder")
    void shufflePreservesImmutability() {
        var builder = Sequence.builder("seq").shuffle(42L).child(Step.of("a", s -> {}));
        var first = builder.build();
        builder.child(Step.of("b", s -> {}));
        var second = builder.build();
        assertThat(first.children()).hasSize(1);
        assertThat(second.children()).hasSize(2);
        assertThat(first.isShuffled()).isTrue();
        assertThat(second.isShuffled()).isTrue();
    }

    @Test
    @DisplayName("shuffle works with dependent")
    void shuffleWorksWithDependent() {
        var seq = Sequence.builder("seq").shuffle().child(Step.of("a", s -> {})).build();
        assertThat(seq.isDependent()).isTrue();
        assertThat(seq.isShuffled()).isTrue();
    }

    @Test
    @DisplayName("shuffle works with independent")
    void shuffleWorksWithIndependent() {
        var seq = Sequence.builder("seq")
                .independent()
                .shuffle()
                .child(Step.of("a", s -> {}))
                .build();
        assertThat(seq.isIndependent()).isTrue();
        assertThat(seq.isShuffled()).isTrue();
    }

    @Test
    @DisplayName("shuffle with seed 0 is valid")
    void shuffleWithSeedZeroIsValid() {
        var seq =
                Sequence.builder("seq").shuffle(0L).child(Step.of("a", s -> {})).build();
        assertThat(seq.isShuffled()).isTrue();
        assertThat(seq.seed()).isZero();
    }

    @Test
    @DisplayName("child(Builder) builds and adds child action")
    void childBuilderBuildsAndAddsChildAction() {
        var seq = Sequence.builder("seq")
                .child(Step.of("a", s -> {}))
                .child(Sequence.builder("nested").child(Step.of("b", s -> {})))
                .build();
        assertThat(seq.children()).hasSize(2);
        assertThat(seq.children().get(1).displayName()).isEqualTo("nested");
    }

    @Test
    @DisplayName("child(Builder) rejects null builder")
    void childBuilderOverloadRejectsNull() {
        var builder = Sequence.builder("seq");
        assertThatThrownBy(() -> builder.child((org.paramixel.api.action.Builder) null))
                .isInstanceOf(NullPointerException.class);
    }
}
