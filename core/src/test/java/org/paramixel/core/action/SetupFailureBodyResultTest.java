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

@DisplayName("Lifecycle Body Result When Setup Fails")
class SetupFailureBodyResultTest {

    @Test
    @DisplayName("when setup fails, body is never executed but receives SKIP result")
    void whenSetupFailsBodyIsNeverExecutedButReceivesSkipResult() {
        var lifecycle = Lifecycle.of(
                "lifecycle",
                context -> {
                    throw new RuntimeException("setup failed");
                },
                Direct.of("body", context -> {
                    throw new RuntimeException("body should not execute");
                }));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.failure()).isPresent();
        assertThat(result.failure().get()).hasMessage("setup failed");
        assertThat(result.children()).hasSize(1);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.SKIP);
        assertThat(result.children().get(0).action()).isInstanceOf(Direct.class);
        assertThat(result.children().get(0).action().name()).isEqualTo("body");
    }

    @Test
    @DisplayName("when setup fails, all nested children in body tree receive SKIP results")
    void whenSetupFailsAllNestedChildrenInBodyTreeReceiveSkipResults() {
        Action leaf1 = Direct.of("leaf1", context -> {});
        Action leaf2 = Direct.of("leaf2", context -> {});
        Action leaf3 = Direct.of("leaf3", context -> {});
        Action body = Sequential.of("sequential", List.of(leaf1, leaf2, leaf3));
        var lifecycle = Lifecycle.of(
                "lifecycle",
                context -> {
                    throw new RuntimeException("setup failed");
                },
                body);

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.failure()).isPresent();
        assertThat(result.failure().get()).hasMessage("setup failed");
        assertThat(result.children()).hasSize(1);

        Result bodyResult = result.children().get(0);
        assertThat(bodyResult.status()).isEqualTo(Result.Status.SKIP);
        assertThat(bodyResult.action()).isSameAs(body);
        assertThat(bodyResult.children()).hasSize(3);

        List<Result> leafResults = bodyResult.children();
        assertThat(leafResults).allMatch(r -> r.status() == Result.Status.SKIP);
        assertThat(leafResults.get(0).action()).isSameAs(leaf1);
        assertThat(leafResults.get(1).action()).isSameAs(leaf2);
        assertThat(leafResults.get(2).action()).isSameAs(leaf3);
    }
}
