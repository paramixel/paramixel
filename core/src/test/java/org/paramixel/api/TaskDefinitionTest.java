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
import static org.paramixel.api.Tasks.forInstance;
import static org.paramixel.api.Tasks.parallel;
import static org.paramixel.api.Tasks.sequential;
import static org.paramixel.api.Tasks.task;

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
        final var builder = sequential("root").step("one", () -> {});
        final Task first = builder.build();
        builder.step("two", () -> {});
        final Task second = builder.build();

        assertThat(first.children()).hasSize(1);
        assertThat(second.children()).hasSize(2);
        assertThat(first.children().get(0).name()).isEqualTo("one");
    }

    @Test
    @DisplayName("attaching a child builder snapshots the subtree")
    void childSnapshotIsIndependent() {
        final var child = sequential("child").step("one", () -> {});
        final var parent = sequential("parent").child(child);
        child.step("two", () -> {});

        final Task parentTask = parent.build();
        assertThat(parentTask.children()).hasSize(1);
        assertThat(parentTask.children().get(0).children()).hasSize(1);
    }

    @Test
    @DisplayName("reusing one definition materializes distinct occurrences")
    void definitionReuseYieldsDistinctOccurrences() {
        final Task shared = task("shared").step("body", () -> {}).build();
        final Task root =
                parallel("root", Pool.named("p")).child(shared).child(shared).build();
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
        assertThatThrownBy(() -> task(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> task(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Pool.named("bad name")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pool.named("4pool")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task("x").timeout(java.time.Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task("x").isolated("bad name")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task("x").tags(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task("x").each(List.of(1, 2), item -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(() -> task("x").repeat("r", repeat -> repeat.step("s", () -> {})))
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
        final Task definition = sequential("matrix")
                .each(List.of("a", "b", "c"), item -> {
                    iterated.add(item);
                    return task(item).step("body", bodyRuns::incrementAndGet);
                })
                .build();

        assertThat(iterated).containsExactly("a", "b", "c");
        assertThat(definition.children()).hasSize(3);
        assertThat(bodyRuns).hasValue(0);
    }

    @Test
    @DisplayName("definition construction performs no user work")
    void constructionIsSideEffectFree() {
        final AtomicInteger factoryCalls = new AtomicInteger();
        final AtomicInteger callbackCalls = new AtomicInteger();
        final Task definition = forInstance("instance", () -> {
                    factoryCalls.incrementAndGet();
                    return new Object();
                })
                .before(callbackCalls::incrementAndGet)
                .step("body", callbackCalls::incrementAndGet)
                .after(callbackCalls::incrementAndGet)
                .build();

        assertThat(factoryCalls).hasValue(0);
        assertThat(callbackCalls).hasValue(0);
        assertThat(definition.children()).hasSize(1);
    }

    @Test
    @DisplayName("a task may have a body and children, and an empty task is allowed")
    void bodyAndChildrenCoexist() {
        final Task withBoth = sequential("both")
                .step("body", () -> {}, children -> children.step("nested", () -> {}))
                .build();
        assertThat(withBoth.children()).hasSize(1);
        assertThat(withBoth.children().get(0).body()).isPresent();
        assertThat(withBoth.children().get(0).children()).hasSize(1);
        assertThat(task("empty").build().isEmpty()).isTrue();
    }
}
