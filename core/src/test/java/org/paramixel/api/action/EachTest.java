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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Each")
class EachTest {

    @Test
    @DisplayName("sequential(Iterable) adds children in item order")
    void sequentialIterableAddsChildrenInItemOrder() {
        var sequential = Each.sequential("test", List.of("first", "second", "third"), value -> Step.of(value, s -> {}))
                .build();

        assertThat(sequential.displayName()).isEqualTo("test");
        assertThat(sequential.children()).hasSize(3);
        assertThat(sequential.children().get(0).displayName()).isEqualTo("first");
        assertThat(sequential.children().get(1).displayName()).isEqualTo("second");
        assertThat(sequential.children().get(2).displayName()).isEqualTo("third");
        assertThat(sequential.isIndependent()).isFalse();
    }

    @Test
    @DisplayName("sequential(Stream) adds children in item order")
    void sequentialStreamAddsChildrenInItemOrder() {
        var sequential = Each.sequential(
                        "test", Stream.of("first", "second", "third"), value -> Step.of(value, s -> {}))
                .build();

        assertThat(sequential.children()).hasSize(3);
        assertThat(sequential.children().get(0).displayName()).isEqualTo("first");
        assertThat(sequential.children().get(1).displayName()).isEqualTo("second");
        assertThat(sequential.children().get(2).displayName()).isEqualTo("third");
    }

    @Test
    @DisplayName("sequential(Stream) with empty stream adds no children")
    void sequentialStreamWithEmptyStreamAddsNoChildren() {
        var sequential = Each.sequential("test", Stream.empty(), value -> Step.of(value.toString(), s -> {}))
                .build();

        assertThat(sequential.children()).isEmpty();
    }

    @Test
    @DisplayName("sequential result can be configured as independent")
    void sequentialResultCanBeConfiguredAsIndependent() {
        var sequential = Each.sequential("test", List.of("child"), value -> Step.of(value, s -> {}))
                .independent()
                .build();

        assertThat(sequential.isIndependent()).isTrue();
    }

    @Test
    @DisplayName("sequential mapper can return builders")
    void sequentialMapperCanReturnBuilders() {
        var sequential = Each.sequential(
                        "test",
                        List.of("child"),
                        value -> Instance.builder(value, Object::new).body(Step.of("body", s -> {})))
                .build();

        assertThat(sequential.children()).hasSize(1);
        assertThat(sequential.children().get(0)).isInstanceOf(Instance.class);
        assertThat(sequential.children().get(0).displayName()).isEqualTo("child");
    }

    @Test
    @DisplayName("sequential(Iterable) with explicit BuilderMapper uses builder results")
    void sequentialIterableWithBuilderMapper() {
        Each.BuilderMapper<String> mapper =
                value -> Instance.builder(value, Object::new).body(Step.of("inner", s -> {}));
        var sequential = Each.sequential("test", List.of("a", "b"), mapper).build();

        assertThat(sequential.children()).hasSize(2);
        assertThat(sequential.children().get(0)).isInstanceOf(Instance.class);
        assertThat(sequential.children().get(0).displayName()).isEqualTo("a");
        assertThat(sequential.children().get(1).displayName()).isEqualTo("b");
    }

    @Test
    @DisplayName("sequential(Stream) with explicit BuilderMapper uses builder results")
    void sequentialStreamWithBuilderMapper() {
        Each.BuilderMapper<String> mapper =
                value -> Instance.builder(value, Object::new).body(Step.of("inner", s -> {}));
        var sequential = Each.sequential("test", Stream.of("x", "y"), mapper).build();

        assertThat(sequential.children()).hasSize(2);
        assertThat(sequential.children().get(0).displayName()).isEqualTo("x");
        assertThat(sequential.children().get(1).displayName()).isEqualTo("y");
    }

    @Test
    @DisplayName("parallel(Iterable) adds children")
    void parallelIterableAddsChildren() {
        var parallel = Each.parallel("test", List.of("/a", "/b", "/c"), value -> Step.of(value, s -> {}))
                .build();

        assertThat(parallel.displayName()).isEqualTo("test");
        assertThat(parallel.children()).hasSize(3);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("/a");
        assertThat(parallel.children().get(1).displayName()).isEqualTo("/b");
        assertThat(parallel.children().get(2).displayName()).isEqualTo("/c");
    }

    @Test
    @DisplayName("parallel(Stream) adds children")
    void parallelStreamAddsChildren() {
        var parallel = Each.parallel("test", Stream.of("/a", "/b", "/c"), value -> Step.of(value, s -> {}))
                .build();

        assertThat(parallel.children()).hasSize(3);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("/a");
        assertThat(parallel.children().get(1).displayName()).isEqualTo("/b");
        assertThat(parallel.children().get(2).displayName()).isEqualTo("/c");
    }

    @Test
    @DisplayName("parallel(Stream) with empty stream adds no children")
    void parallelStreamWithEmptyStreamAddsNoChildren() {
        var parallel = Each.parallel("test", Stream.empty(), value -> Step.of(value.toString(), s -> {}))
                .build();

        assertThat(parallel.children()).isEmpty();
    }

    @Test
    @DisplayName("parallel result can configure parallelism")
    void parallelResultCanConfigureParallelism() {
        var parallel = Each.parallel("test", List.of("child"), value -> Step.of(value, s -> {}))
                .parallelism(2)
                .build();

        assertThat(parallel.parallelism()).isEqualTo(2);
    }

    @Test
    @DisplayName("parallel mapper can return builders")
    void parallelMapperCanReturnBuilders() {
        var parallel = Each.parallel(
                        "test",
                        List.of("child"),
                        value -> Instance.builder(value, Object::new).body(Step.of("body", s -> {})))
                .build();

        assertThat(parallel.children()).hasSize(1);
        assertThat(parallel.children().get(0)).isInstanceOf(Instance.class);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("child");
    }

    @Test
    @DisplayName("parallel(Iterable) with explicit BuilderMapper uses builder results")
    void parallelIterableWithBuilderMapper() {
        Each.BuilderMapper<String> mapper =
                value -> Instance.builder(value, Object::new).body(Step.of("inner", s -> {}));
        var parallel = Each.parallel("test", List.of("p", "q"), mapper).build();

        assertThat(parallel.children()).hasSize(2);
        assertThat(parallel.children().get(0)).isInstanceOf(Instance.class);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("p");
        assertThat(parallel.children().get(1).displayName()).isEqualTo("q");
    }

    @Test
    @DisplayName("parallel(Stream) with explicit BuilderMapper uses builder results")
    void parallelStreamWithBuilderMapper() {
        Each.BuilderMapper<String> mapper =
                value -> Instance.builder(value, Object::new).body(Step.of("inner", s -> {}));
        var parallel = Each.parallel("test", Stream.of("m", "n"), mapper).build();

        assertThat(parallel.children()).hasSize(2);
        assertThat(parallel.children().get(0).displayName()).isEqualTo("m");
        assertThat(parallel.children().get(1).displayName()).isEqualTo("n");
    }

    @Test
    @DisplayName("sequential rejects null iterable items")
    void sequentialRejectsNullIterableItems() {
        assertThatThrownBy(() -> Each.sequential("test", (Iterable<String>) null, value -> Step.of("x", s -> {})))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("items is null");
    }

    @Test
    @DisplayName("sequential rejects null stream items")
    void sequentialRejectsNullStreamItems() {
        assertThatThrownBy(() -> Each.sequential("test", (Stream<String>) null, value -> Step.of("x", s -> {})))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("items is null");
    }

    @Test
    @DisplayName("parallel rejects null iterable items")
    void parallelRejectsNullIterableItems() {
        assertThatThrownBy(() -> Each.parallel("test", (Iterable<String>) null, value -> Step.of("x", s -> {})))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("items is null");
    }

    @Test
    @DisplayName("parallel rejects null stream items")
    void parallelRejectsNullStreamItems() {
        assertThatThrownBy(() -> Each.parallel("test", (Stream<String>) null, value -> Step.of("x", s -> {})))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("items is null");
    }

    @Test
    @DisplayName("sequential rejects null mapper")
    void sequentialRejectsNullMapper() {
        assertThatThrownBy(() -> Each.sequential("test", List.of("a"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper is null");
    }

    @Test
    @DisplayName("parallel rejects null mapper")
    void parallelRejectsNullMapper() {
        assertThatThrownBy(() -> Each.parallel("test", List.of("a"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mapper is null");
    }

    @Test
    @DisplayName("sequential rejects null mapper result")
    void sequentialRejectsNullMapperResult() {
        assertThatThrownBy(() -> Each.sequential("test", List.of("a"), value -> null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action is null");
    }

    @Test
    @DisplayName("sequential BuilderMapper returning null is rejected")
    void sequentialBuilderMapperReturnsNull() {
        Each.BuilderMapper<String> nullMapper = value -> null;
        assertThatThrownBy(() -> Each.sequential("test", List.of("a"), nullMapper))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action is null");
    }

    @Test
    @DisplayName("sequential rejects unsupported mapper return type")
    void sequentialRejectsUnsupportedMapperReturnType() {
        assertThatThrownBy(() -> Each.sequential("test", List.of("a"), value -> "not an action"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported action type");
    }
}
