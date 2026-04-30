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

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("before failed");
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(lifecycle.getChildren()).hasSize(3);
        assertThat(lifecycle.getChildren().get(0).getResult().getStatus().isFailure())
                .isTrue();
        assertThat(lifecycle.getChildren().get(0)).isInstanceOf(Direct.class);
        assertThat(lifecycle.getChildren().get(0).getName()).isEqualTo("before");
        assertThat(lifecycle.getChildren().get(1).getResult().getStatus().isSkip())
                .isTrue();
        assertThat(lifecycle.getChildren().get(1)).isInstanceOf(Direct.class);
        assertThat(lifecycle.getChildren().get(1).getName()).isEqualTo("main");
        assertThat(lifecycle.getChildren().get(2).getResult().getStatus().isPass())
                .isTrue();
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

        Runner runner = Runner.builder().build();
        runner.run(lifecycle);
        Result result = lifecycle.getResult();

        assertThat(result.getStatus().isFailure()).isTrue();
        assertThat(result.getStatus().getMessage()).isPresent();
        assertThat(result.getStatus().getMessage().get()).isEqualTo("before failed");
        assertThat(result.getStatus().getThrowable()).isEmpty();
        assertThat(lifecycle.getChildren()).hasSize(3);

        Result mainResult = main.getResult();
        assertThat(mainResult.getStatus().isSkip()).isTrue();
        assertThat(main).isSameAs(main);
        assertThat(main.getChildren()).hasSize(3);

        assertThat(leaf1.getResult().getStatus().isSkip()).isTrue();
        assertThat(leaf2.getResult().getStatus().isSkip()).isTrue();
        assertThat(leaf3.getResult().getStatus().isSkip()).isTrue();
    }
}
