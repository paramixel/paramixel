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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;

@DisplayName("Sequential.Builder.each")
@org.junit.jupiter.api.Timeout(60)
class SequentialEachTest {

    private static Runner runner() {
        return Runner.builder().configuration(Configuration.of(Map.of())).build();
    }

    private static Function<String, ActionBuilder> recorder(final List<String> events) {
        return value -> Step.step(value, context -> events.add(value));
    }

    @Test
    @DisplayName("each adds one child per item, in iteration order")
    void addsOneChildPerItem() {
        var sequential = Sequential.sequential("s")
                .each(List.of("a", "b", "c"), value -> Step.step(value, context -> {}))
                .build();

        assertThat(sequential.children()).extracting(Action::displayName).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("each runs the mapped children in order")
    void runsMappedChildrenInOrder() {
        var events = new CopyOnWriteArrayList<String>();

        var result = runner().run(Sequential.sequential("s")
                .each(List.of("a", "b", "c"), recorder(events))
                .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(events).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("each mixes with child before and after in declaration order")
    void mixesWithChildInDeclarationOrder() {
        var sequential = Sequential.sequential("s")
                .child(Step.step("first", context -> {}))
                .each(List.of("m1", "m2"), value -> Step.step(value, context -> {}))
                .child(Step.step("last", context -> {}))
                .build();

        assertThat(sequential.children()).extracting(Action::displayName).containsExactly("first", "m1", "m2", "last");
    }

    @Test
    @DisplayName("each over an empty iterable adds no children")
    void emptyIterableAddsNoChildren() {
        var sequential = Sequential.sequential("s")
                .each(List.<String>of(), value -> Step.step(value, context -> {}))
                .build();

        assertThat(sequential.children()).isEmpty();
        assertThat(runner().run(sequential).status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("null items, a null mapper and a null mapped result are rejected")
    void rejectsNulls() {
        var builder = Sequential.sequential("s");

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
    @DisplayName("independent() applies to the mapped children")
    void independentAppliesToMappedChildren() {
        var events = new CopyOnWriteArrayList<String>();

        runner().run(Sequential.sequential("s")
                .independent()
                .each(
                        List.of("a", "b"),
                        value -> Step.step(value, context -> {
                            events.add(value);
                            if ("a".equals(value)) {
                                throw org.paramixel.api.exception.FailException.fail("a failed");
                            }
                        }))
                .build());

        assertThat(events).containsExactly("a", "b");
    }

    @Test
    @DisplayName("children(Iterable) adds already built children in order")
    void bulkChildren() {
        var sequential = Sequential.sequential("s")
                .children(List.of(Step.step("a", context -> {}), Step.step("b", context -> {})))
                .build();

        assertThat(sequential.children()).extracting(Action::displayName).containsExactly("a", "b");
    }
}
