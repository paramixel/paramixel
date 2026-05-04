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

package org.paramixel.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

@DisplayName("Lifecycle Main Result When Before Fails")
class BeforeFailureMainResultTest {

    @Test
    @DisplayName("when before fails, main is never executed but receives SKIP result")
    void whenBeforeFailsMainIsNeverExecutedButReceivesSkipResult() {
        var lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw new RuntimeException("before failed");
                }),
                Direct.of("main", context -> {
                    throw new RuntimeException("main should not execute");
                }),
                Noop.of("noop"));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("before failed");
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(lifecycle.getChildren()).hasSize(3);
        assertThat(result.getChildren().get(0).getStatus().isFailure()).isTrue();
        assertThat(lifecycle.getChildren().get(0)).isInstanceOf(Direct.class);
        assertThat(lifecycle.getChildren().get(0).getName()).isEqualTo("before");
        assertThat(result.getChildren().get(1).getStatus().isSkip()).isTrue();
        assertThat(lifecycle.getChildren().get(1)).isInstanceOf(Direct.class);
        assertThat(lifecycle.getChildren().get(1).getName()).isEqualTo("main");
        assertThat(result.getChildren().get(2).getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("when before fails, all nested children in main tree receive SKIP results")
    void whenBeforeFailsAllNestedGetChildrenInMainTreeReceiveSkipResults() {
        Action leaf1 = Direct.of("leaf1", context -> {});
        Action leaf2 = Direct.of("leaf2", context -> {});
        Action leaf3 = Direct.of("leaf3", context -> {});
        Action main = Sequential.of("sequential", List.of(leaf1, leaf2, leaf3));
        var lifecycle = Lifecycle.of(
                "lifecycle",
                Direct.of("before", context -> {
                    throw new RuntimeException("before failed");
                }),
                main,
                Noop.of("after"));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("before failed");
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(lifecycle.getChildren()).hasSize(3);

        Result mainResult = result.getChildren().get(1);
        assertThat(mainResult.getStatus().isSkip()).isTrue();
        assertThat(main.getChildren()).hasSize(3);

        assertThat(mainResult.getChildren().get(0).getStatus().isSkip()).isTrue();
        assertThat(mainResult.getChildren().get(1).getStatus().isSkip()).isTrue();
        assertThat(mainResult.getChildren().get(2).getStatus().isSkip()).isTrue();
    }
}
