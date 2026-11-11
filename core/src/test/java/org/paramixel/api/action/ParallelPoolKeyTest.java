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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;

/**
 * Pins §4.1: the runner's root pool and a user pool named {@code root} are separate budgets with separate keys.
 */
@DisplayName("Parallel pool keys")
@org.junit.jupiter.api.Timeout(60)
class ParallelPoolKeyTest {

    private static Runner runner(final Map<String, String> configuration) {
        return Runner.builder().configuration(Configuration.of(configuration)).build();
    }

    @Test
    @DisplayName("a user pool named root is sized by paramixel.parallelism.root")
    void userPoolNamedRootIsSizedByItsOwnKey() {
        var action = Parallel.parallel("x", "root")
                .child(Step.step("child", context -> {}))
                .build();

        var result = runner(Map.of(Configuration.poolKey("root"), "1")).run(action);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("a missing key for the pool named root fails and does not fall back to paramixel.parallelism")
    void missingUserRootKeyDoesNotFallBack() {
        var action = Parallel.parallel("x", "root")
                .child(Step.step("child", context -> {}))
                .build();

        var result = runner(Map.of(Configuration.RUNNER_PARALLELISM, "4")).run(action);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.status().message().orElseThrow()).contains(Configuration.poolKey("root"));
    }

    @Test
    @DisplayName("a user pool named root and the runner root pool are independent budgets")
    void rootPoolAndUserRootAreIndependent() {
        // The runner's root pool is 4 and the user pool named root is 1, so the user pool bounds the
        // children to one at a time even though the root pool would allow more.
        var action = Parallel.parallel("x", "root")
                .child(Step.step("one", context -> {}))
                .child(Step.step("two", context -> {}))
                .build();

        var result = runner(Map.of(Configuration.RUNNER_PARALLELISM, "4", Configuration.poolKey("root"), "1"))
                .run(action);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
        assertThat(result.descriptor().orElseThrow().children()).hasSize(2);
    }

    @Test
    @DisplayName("the pool name root is accepted like any other user pool name")
    void rootIsAnOrdinaryPoolName() {
        for (var name : List.of("root", "docker", "environments")) {
            assertThat(Parallel.parallel("x", name)
                            .child(Step.step("child", context -> {}))
                            .build()
                            .pool())
                    .isEqualTo(name);
        }
    }

    @Test
    @DisplayName("the reserved root pool name is not part of the user pool-name grammar")
    void reservedRootNameIsNotAUserPoolName() {
        assertThatThrownBy(() -> Parallel.parallel("x", "$root").child(Step.step("s", context -> {})))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
