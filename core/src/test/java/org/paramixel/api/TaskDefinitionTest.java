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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Structural tests for the immutable definition model and the authoring surface.
 */
class TaskDefinitionTest {

    @Test
    @DisplayName("build snapshots are independent of later builder mutation")
    void builderSnapshotIsIndependent() {
        final var builder = Sequential.builder("root").step("one", () -> {});
        final Task first = builder.build();
        builder.step("two", () -> {});
        final Task second = builder.build();

        assertThat(first.children()).hasSize(1);
        assertThat(second.children()).hasSize(2);
        assertThat(first.children().get(0).name()).isEqualTo("one");
    }

    @Test
    @DisplayName("a step builder is held until the parent is built")
    void stepBuilderIsHeldUntilBuild() {
        final var child = Sequential.builder("child").step("one", () -> {});
        final var parent = Sequential.builder("parent").step(child);
        child.step("two", () -> {});

        final Task parentTask = parent.build();
        assertThat(parentTask.children()).hasSize(1);
        assertThat(parentTask.children().get(0).children()).hasSize(2);
    }

    @Test
    @DisplayName("reusing one definition materializes distinct occurrences")
    void definitionReuseYieldsDistinctOccurrences() {
        final Task shared = Sequential.builder("shared").step("body", () -> {}).build();
        final Task root = Parallel.builder("root", Pool.named("p"))
                .step(shared)
                .step(shared)
                .build();
        final var result = Runner.builder()
                .listener(Listener.NOOP)
                .pool(Pool.named("p"), 2)
                .build()
                .run(root);

        final var children = result.root().orElseThrow().children();
        assertThat(children).hasSize(2);
        assertThat(children.get(0).occurrenceId()).isNotEqualTo(children.get(1).occurrenceId());
        assertThat(children.get(0).definitionId()).isEqualTo(children.get(1).definitionId());
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("names, pools, and numeric policies are validated at definition time")
    void definitionTimeValidation() {
        assertThatThrownBy(() -> Sequential.builder(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> Sequential.builder(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Pool.named("bad name")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pool.named("4pool")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sequential.builder("x").timeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sequential.builder("x").isolated("bad name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sequential.builder("x").tags(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sequential.builder("x").each(List.of(1, 2), item -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(() -> Sequential.builder("x")
                        .repeat("r", repeat -> repeat.step("s", () -> {}))
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maxIterations");
    }

    @Test
    @DisplayName("pool values compare by name and allocate no threads")
    void poolValueEquality() {
        final var before = Thread.activeCount();
        final Pool first = Pool.named("environments");
        final Pool second = Pool.named("environments");
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(Pool.named("Environments")).isNotEqualTo(first);
        assertThat(Thread.activeCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("the legacy action hierarchy is gone")
    void legacyActionApiIsRemoved() {
        assertThatThrownBy(() -> Class.forName("org.paramixel.api.action.Action"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("org.paramixel.api.Descriptor"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("nonapi.org.paramixel.Engine"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("each expands eagerly at definition time without running bodies")
    void eachExpandsEagerly() {
        final AtomicInteger bodyRuns = new AtomicInteger();
        final List<String> iterated = new ArrayList<>();
        final Task definition = Sequential.builder("matrix")
                .each(List.of("a", "b", "c"), item -> {
                    iterated.add(item);
                    return Sequential.builder(item).step("body", bodyRuns::incrementAndGet);
                })
                .build();

        assertThat(iterated).containsExactly("a", "b", "c");
        assertThat(definition.children()).hasSize(3);
        assertThat(bodyRuns).hasValue(0);
    }

    @Test
    @DisplayName("definition construction performs no user work")
    void constructionIsSideEffectFree() {
        final AtomicInteger callbackCalls = new AtomicInteger();
        final Task definition = Sequential.builder("group")
                .before(callbackCalls::incrementAndGet)
                .step("body", callbackCalls::incrementAndGet)
                .after(callbackCalls::incrementAndGet)
                .build();

        assertThat(callbackCalls).hasValue(0);
        assertThat(definition.children()).hasSize(1);
    }

    @Test
    @DisplayName("a task may have a body and children, and an empty task is allowed")
    void bodyAndChildrenCoexist() {
        final Task withBoth = Sequential.builder("both")
                .step("body", () -> {}, children -> children.step("nested", () -> {}))
                .build();
        assertThat(withBoth.children()).hasSize(1);
        assertThat(withBoth.children().get(0).body()).isPresent();
        assertThat(withBoth.children().get(0).children()).hasSize(1);
        assertThat(Sequential.builder("empty").build().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("an unseeded shuffle chooses its seed on the first build and later builds reuse it")
    void unseededShuffleSeedIsChosenOnce() {
        final var builder = Sequential.builder("root")
                .step("a", () -> {})
                .step("b", () -> {})
                .shuffle();

        final var first = builder.build();
        final var second = builder.build();

        assertThat(first.shuffleSeed()).isPresent();
        assertThat(second.shuffleSeed()).isEqualTo(first.shuffleSeed());
    }

    @Test
    @DisplayName("the most recent shuffle call wins: shuffle() discards an earlier explicit seed")
    void unseededShuffleDiscardsEarlierSeed() {
        final long explicit = 42L;

        final var reseeded = Sequential.builder("root")
                .step("a", () -> {})
                .step("b", () -> {})
                .shuffle(explicit)
                .shuffle()
                .build();
        final var seeded = Sequential.builder("root")
                .step("a", () -> {})
                .step("b", () -> {})
                .shuffle()
                .shuffle(explicit)
                .build();

        assertThat(reseeded.shuffleSeed())
                .isPresent()
                .hasValueSatisfying(seed -> assertThat(seed).isNotEqualTo(explicit));
        assertThat(seeded.shuffleSeed()).hasValue(explicit);
    }

    @Test
    @DisplayName("execution metadata alone does not make a task non-empty")
    void executionMetadataIsNotWork() {
        assertThat(Sequential.builder("lock-only").isolated("db").build().isEmpty())
                .isTrue();
        assertThat(Sequential.builder("timeout-only")
                        .timeout(Duration.ofSeconds(1))
                        .build()
                        .isEmpty())
                .isTrue();
        assertThat(Sequential.builder("tags-only").tags("smoke").build().isEmpty())
                .isTrue();
        assertThat(Sequential.builder("with-body")
                        .step("work", () -> {})
                        .build()
                        .isEmpty())
                .isFalse();
    }
}
