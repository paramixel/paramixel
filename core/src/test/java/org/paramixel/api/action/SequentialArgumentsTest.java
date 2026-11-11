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

@DisplayName("Sequential arguments")
class SequentialArgumentsTest {

    @Test
    @DisplayName("builder validates required inputs")
    void builderValidatesRequiredInputs() {
        assertThatThrownBy(() -> Sequential.sequential(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Sequential.sequential(" ")).isInstanceOf(IllegalArgumentException.class);
        var seq = Sequential.sequential("empty").build();
        assertThat(seq.children()).isEmpty();
    }

    @Test
    @DisplayName("child(Action) rejects null builder")
    void childBuilderRejectsNull() {
        var builder = Sequential.sequential("seq");
        assertThatThrownBy(() -> builder.child((Action) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("child(Action) adds child from builder")
    void childBuilderAddsChild() {
        var seq = Sequential.sequential("seq").child(Step.step("step", s -> {})).build();
        assertThat(seq.children()).hasSize(1);
        assertThat(seq.children().get(0).displayName()).isEqualTo("step");
    }

    @Test
    @DisplayName("builder accumulates multiple children")
    void builderAccumulatesMultipleChildren() {
        var seq = Sequential.sequential("seq")
                .child(Step.step("first", s -> {}))
                .child(Step.step("second", s -> {}))
                .child(Step.step("third", s -> {}))
                .build();
        assertThat(seq.children()).hasSize(3);
        assertThat(seq.children().get(0).displayName()).isEqualTo("first");
        assertThat(seq.children().get(1).displayName()).isEqualTo("second");
        assertThat(seq.children().get(2).displayName()).isEqualTo("third");
    }

    @Test
    @DisplayName("builder can build multiple immutable snapshots")
    void builderCanBuildMultipleImmutableSnapshots() {
        var builder = Sequential.sequential("seq");
        builder.child(Step.step("test", s -> {}));
        var first = builder.build();
        builder.child(Step.step("other", s -> {}));
        builder.independent();
        var second = builder.build();

        assertThat(first.children()).extracting(Action::displayName).containsExactly("test");
        assertThat(first.isDependent()).isTrue();
        assertThat(second.children()).extracting(Action::displayName).containsExactly("test", "other");
        assertThat(second.isDependent()).isFalse();
    }

    @Test
    @DisplayName("default is dependent")
    void defaultIsDependent() {
        var seq = Sequential.sequential("seq").child(Step.step("test", s -> {})).build();
        assertThat(seq.isDependent()).isTrue();
        assertThat(seq.isDependent()).isTrue();
    }

    @Test
    @DisplayName("independent() configures as independent")
    void independentConfiguresAsIndependent() {
        var seq = Sequential.sequential("seq")
                .independent()
                .child(Step.step("test", s -> {}))
                .build();
        assertThat(seq.isDependent()).isFalse();
        assertThat(seq.isDependent()).isFalse();
    }

    @Test
    @DisplayName("dependent() after independent() restores dependent")
    void dependentAfterIndependentRestoresDependent() {
        var seq = Sequential.sequential("seq")
                .independent()
                .dependent()
                .child(Step.step("test", s -> {}))
                .build();
        assertThat(seq.isDependent()).isTrue();
    }

    @Test
    @DisplayName("default shuffleSeed is empty")
    void defaultNotShuffled() {
        var seq = Sequential.sequential("seq").child(Step.step("a", s -> {})).build();
        assertThat(seq.shuffleSeed()).isEmpty();
    }

    @Test
    @DisplayName("shuffle() sets a non-zero shuffleSeed")
    void shuffleSetsShuffledAndSeed() {
        var seq = Sequential.sequential("seq")
                .shuffle()
                .child(Step.step("a", s -> {}))
                .child(Step.step("b", s -> {}))
                .build();
        assertThat(seq.shuffleSeed()).isPresent();
        assertThat(seq.shuffleSeed().orElseThrow()).isNotZero();
    }

    @Test
    @DisplayName("shuffle(long seed) stores explicit seed")
    void shuffleWithSeedStoresSeed() {
        var seq = Sequential.sequential("seq")
                .shuffle(42L)
                .child(Step.step("a", s -> {}))
                .child(Step.step("b", s -> {}))
                .build();
        assertThat(seq.shuffleSeed()).isPresent();
        assertThat(seq.shuffleSeed().orElseThrow()).isEqualTo(42L);
    }

    @Test
    @DisplayName("shuffle with known seed produces predictable order")
    void shuffleWithKnownSeedProducesPredictableOrder() {
        var seq = Sequential.sequential("seq")
                .shuffle(42L)
                .child(Step.step("a", s -> {}))
                .child(Step.step("b", s -> {}))
                .child(Step.step("c", s -> {}))
                .build();
        var names = seq.children().stream().map(Action::displayName).toList();
        var seq2 = Sequential.sequential("seq")
                .shuffle(42L)
                .child(Step.step("a", s -> {}))
                .child(Step.step("b", s -> {}))
                .child(Step.step("c", s -> {}))
                .build();
        assertThat(seq2.children().stream().map(Action::displayName).toList()).isEqualTo(names);
    }

    @Test
    @DisplayName("shuffle with 0 children still reports shuffled")
    void shuffleWithZeroChildrenStillShuffled() {
        var seq = Sequential.sequential("seq").shuffle().build();
        assertThat(seq.shuffleSeed()).isPresent();
        assertThat(seq.shuffleSeed().orElseThrow()).isNotZero();
        assertThat(seq.children()).isEmpty();
    }

    @Test
    @DisplayName("shuffle with 1 child is no-op but still shuffled")
    void shuffleSingleChildStillShuffled() {
        var seq = Sequential.sequential("seq")
                .shuffle()
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(seq.shuffleSeed()).isPresent();
        assertThat(seq.shuffleSeed().orElseThrow()).isNotZero();
        assertThat(seq.children()).hasSize(1);
    }

    @Test
    @DisplayName("shuffle preserves immutability/reusability of builder")
    void shufflePreservesImmutability() {
        var builder = Sequential.sequential("seq").shuffle(42L).child(Step.step("a", s -> {}));
        var first = builder.build();
        builder.child(Step.step("b", s -> {}));
        var second = builder.build();
        assertThat(first.children()).hasSize(1);
        assertThat(second.children()).hasSize(2);
        assertThat(first.shuffleSeed()).isPresent();
        assertThat(second.shuffleSeed()).isPresent();
    }

    @Test
    @DisplayName("shuffle works with dependent")
    void shuffleWorksWithDependent() {
        var seq = Sequential.sequential("seq")
                .shuffle()
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(seq.isDependent()).isTrue();
        assertThat(seq.shuffleSeed()).isPresent();
    }

    @Test
    @DisplayName("shuffle works with independent")
    void shuffleWorksWithIndependent() {
        var seq = Sequential.sequential("seq")
                .independent()
                .shuffle()
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(seq.isDependent()).isFalse();
        assertThat(seq.shuffleSeed()).isPresent();
    }

    @Test
    @DisplayName("shuffle with seed 0 is valid")
    void shuffleWithSeedZeroIsValid() {
        var seq = Sequential.sequential("seq")
                .shuffle(0L)
                .child(Step.step("a", s -> {}))
                .build();
        assertThat(seq.shuffleSeed()).isPresent();
        assertThat(seq.shuffleSeed().orElseThrow()).isZero();
    }

    @Test
    @DisplayName("child(Builder) builds and adds child action")
    void childBuilderBuildsAndAddsChildAction() {
        var seq = Sequential.sequential("seq")
                .child(Step.step("a", s -> {}))
                .child(Sequential.sequential("nested").child(Step.step("b", s -> {})))
                .build();
        assertThat(seq.children()).hasSize(2);
        assertThat(seq.children().get(1).displayName()).isEqualTo("nested");
    }

    @Test
    @DisplayName("child(Builder) rejects null builder")
    void childBuilderOverloadRejectsNull() {
        var builder = Sequential.sequential("seq");
        assertThatThrownBy(() -> builder.child((org.paramixel.api.action.ActionBuilder) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("sequential() is alias for builder()")
    void sequentialIsAliasForBuilder() {
        var seq = Sequential.sequential("seq").child(Step.step("step", s -> {})).build();
        assertThat(seq.displayName()).isEqualTo("seq");
        assertThat(seq.children()).hasSize(1);
    }

    @Test
    @DisplayName("sequential() rejects null")
    void sequentialRejectsNull() {
        assertThatThrownBy(() -> Sequential.sequential(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("sequential() rejects blank")
    void sequentialRejectsBlank() {
        assertThatThrownBy(() -> Sequential.sequential(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
