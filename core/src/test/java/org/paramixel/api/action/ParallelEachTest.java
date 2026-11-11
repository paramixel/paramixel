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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;

@DisplayName("Parallel.Builder.each")
@org.junit.jupiter.api.Timeout(60)
class ParallelEachTest {

    private static Runner runner() {
        return Runner.builder()
                .configuration(Configuration.of(Map.of("paramixel.parallelism.test", "4")))
                .build();
    }

    private static Function<String, ActionBuilder> recorder(final List<String> events) {
        return value -> Step.step(value, context -> events.add(value));
    }

    @Test
    @DisplayName("each adds one child per item, in iteration order")
    void addsOneChildPerItem() {
        var parallel = Parallel.parallel("p", "test")
                .each(List.of("a", "b", "c"), value -> Step.step(value, context -> {}))
                .build();

        assertThat(parallel.children()).extracting(Action::displayName).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("each runs every mapped child")
    void runsEveryMappedChild() {
        var events = new CopyOnWriteArrayList<String>();

        runner().run(Parallel.parallel("p", "test")
                .each(List.of("a", "b", "c"), recorder(events))
                .build());

        assertThat(events).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    @DisplayName("each mixes with child before and after in declaration order")
    void mixesWithChildInDeclarationOrder() {
        var parallel = Parallel.parallel("serial", "test")
                .child(Step.step("first", context -> {}))
                .each(List.of("m1", "m2"), value -> Step.step(value, context -> {}))
                .child(Step.step("last", context -> {}))
                .build();

        assertThat(parallel.children()).extracting(Action::displayName).containsExactly("first", "m1", "m2", "last");
    }

    @Test
    @DisplayName("each over an empty iterable adds no children")
    void emptyIterableAddsNoChildren() {
        var parallel = Parallel.parallel("p", "test")
                .each(List.<String>of(), value -> Step.step(value, context -> {}))
                .build();

        assertThat(parallel.children()).isEmpty();
        var result = runner().run(parallel);
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("the mapper runs once per item at define time")
    void mapperRunsEagerly() {
        var calls = new AtomicInteger();

        Parallel.parallel("p", "test").each(List.of("a", "b"), value -> {
            calls.incrementAndGet();
            return Step.step(value, context -> {});
        });

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a mapper throw is a define-time failure")
    void mapperThrowIsDefineTime() {
        var calls = new AtomicInteger();
        var builder = Parallel.parallel("p", "test");

        assertThatThrownBy(() -> builder.each(List.of("a", "b"), value -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new IllegalStateException("boom");
                    }
                    return Step.step(value, context -> {});
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        // Children added before the throw stay on the builder.
        assertThat(builder.build().children()).extracting(Action::displayName).containsExactly("a");
    }

    @Test
    @DisplayName("null items, a null mapper and a null mapped result are rejected")
    void rejectsNulls() {
        var builder = Parallel.parallel("p", "test");

        assertThatThrownBy(() -> builder.each(null, value -> Step.step("s", context -> {})))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("items is null");
        assertThatThrownBy(() -> builder.each(List.of("a"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper is null");
        assertThatThrownBy(() -> builder.each(List.of("a"), value -> null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper returned null");
    }

    @Test
    @DisplayName("iterating a stream requires the caller to materialize it")
    void streamCallersMaterialize() {
        var names = new ArrayList<>(List.of("a", "b"));
        var parallel = Parallel.parallel("p", "test")
                .each(names.stream().toList(), value -> Step.step(value, context -> {}))
                .build();

        assertThat(parallel.children()).hasSize(2);
    }

    @Test
    @DisplayName("children(Iterable) adds already built children in order")
    void bulkChildren() {
        var parallel = Parallel.parallel("p", "test")
                .children(List.of(Step.step("a", context -> {}), Step.step("b", context -> {})))
                .build();

        assertThat(parallel.children()).extracting(Action::displayName).containsExactly("a", "b");
    }
}
