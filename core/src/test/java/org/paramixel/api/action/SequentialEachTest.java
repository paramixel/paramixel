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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sequential.each")
class SequentialEachTest {

    @Test
    @DisplayName("each() adds children for each item in the iterable")
    void eachAddsChildrenForEachItem() {
        var seq = Sequential.of("test")
                .each(List.of("first", "second", "third"), value -> Step.of(value, s -> {}))
                .resolve();
        assertThat(seq.children()).hasSize(3);
        assertThat(seq.children().get(0).name()).isEqualTo("first");
        assertThat(seq.children().get(1).name()).isEqualTo("second");
        assertThat(seq.children().get(2).name()).isEqualTo("third");
    }

    @Test
    @DisplayName("each() with empty iterable adds no children")
    void eachWithEmptyIterableAddsNoChildren() {
        var seq = Sequential.of("test")
                .each(List.of(), value -> Step.of(value.toString(), s -> {}))
                .resolve();
        assertThat(seq.children()).isEmpty();
    }

    @Test
    @DisplayName("each(Iterable) rejects null items")
    void eachIterableRejectsNullItems() {
        var spec = Sequential.of("test");
        assertThatThrownBy(() -> spec.each((Iterable<String>) null, value -> Step.of("x", s -> {})))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("each() rejects null mapper")
    void eachRejectsNullMapper() {
        var spec = Sequential.of("test");
        assertThatThrownBy(() -> spec.each(List.of("a"), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("mapper exception propagates at spec-building time")
    void mapperExceptionPropagates() {
        var spec = Sequential.of("test");
        assertThatThrownBy(() -> spec.each(List.of("a"), value -> {
                    throw new RuntimeException("mapper exploded");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mapper exploded");
    }

    @Test
    @DisplayName("each() can be chained after child()")
    void eachChainableAfterChild() {
        var seq = Sequential.of("test")
                .child("manual", s -> {})
                .each(List.of("auto"), value -> Step.of(value, s -> {}))
                .resolve();
        assertThat(seq.children()).hasSize(2);
        assertThat(seq.children().get(0).name()).isEqualTo("manual");
        assertThat(seq.children().get(1).name()).isEqualTo("auto");
    }

    @Test
    @DisplayName("each(Stream) adds children for each item in the stream")
    void eachStreamAddsChildrenForEachItem() {
        var seq = Sequential.of("test")
                .each(Stream.of("first", "second", "third"), value -> Step.of(value, s -> {}))
                .resolve();
        assertThat(seq.children()).hasSize(3);
        assertThat(seq.children().get(0).name()).isEqualTo("first");
        assertThat(seq.children().get(1).name()).isEqualTo("second");
        assertThat(seq.children().get(2).name()).isEqualTo("third");
    }

    @Test
    @DisplayName("each(Stream) with empty stream adds no children")
    void eachStreamWithEmptyStreamAddsNoChildren() {
        var seq = Sequential.of("test")
                .each(Stream.empty(), value -> Step.of(value.toString(), s -> {}))
                .resolve();
        assertThat(seq.children()).isEmpty();
    }

    @Test
    @DisplayName("each(Stream) rejects null items")
    void eachStreamRejectsNullItems() {
        var spec = Sequential.of("test");
        assertThatThrownBy(() -> spec.each((Stream<String>) null, value -> Step.of("x", s -> {})))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("each() after resolve() throws IllegalStateException")
    void eachAfterResolveThrows() {
        var spec = Sequential.of("test");
        spec.resolve();
        assertThatIllegalStateException().isThrownBy(() -> spec.each(List.of("x"), value -> Step.of(value, s -> {})));
    }
}
