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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.action.Isolated;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;

/**
 * Pins the plan-validation rejections: they happen before any user code runs, and every problem is reported at once.
 */
@DisplayName("Plan validation")
@org.junit.jupiter.api.Timeout(60)
class PlanValidatorTest {

    private static Result run(final Map<String, String> configuration, final org.paramixel.api.action.Action action) {
        return Runner.builder()
                .configuration(Configuration.of(configuration))
                .build()
                .run(action);
    }

    @Test
    @DisplayName("an undefined pool is reported with its configuration key before any step runs")
    void undefinedPoolReported() {
        var result = run(
                Map.of(),
                Parallel.parallel("p", "docker")
                        .child(Step.step("child", context -> {}))
                        .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.status().message().orElseThrow()).contains("paramixel.parallelism.docker");
    }

    @Test
    @DisplayName("every undefined pool is reported in one message")
    void everyUndefinedPoolReportedTogether() {
        var result = run(
                Map.of(),
                Sequential.sequential("root")
                        .child(Parallel.parallel("a", "docker")
                                .child(Step.step("x", context -> {}))
                                .build())
                        .child(Parallel.parallel("b", "kafka")
                                .child(Step.step("y", context -> {}))
                                .build())
                        .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        var message = result.status().message().orElseThrow();
        assertThat(message).contains("paramixel.parallelism.docker");
        assertThat(message).contains("paramixel.parallelism.kafka");
    }

    @Test
    @DisplayName("a pool nested under itself is rejected with a hint")
    void nestedSamePoolRejected() {
        var result = run(
                Map.of("paramixel.parallelism.p", "2"),
                Parallel.parallel("outer", "p")
                        .child(Parallel.parallel("inner", "p")
                                .child(Step.step("child", context -> {}))
                                .build())
                        .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.status().message().orElseThrow()).contains("cannot be nested under itself");
    }

    @Test
    @DisplayName("resources that can be taken in conflicting order are rejected")
    void conflictingResourceOrderRejected() {
        var step = Step.step("leaf", context -> {});
        var action = Parallel.parallel("root", "p1")
                .child(Isolated.isolated("lock-first", "L")
                        .body(Parallel.parallel("then-pool", "p2").child(step)))
                .child(Parallel.parallel("pool-first", "p2")
                        .child(Isolated.isolated("then-lock", "L").body(step)))
                .build();

        var result = run(Map.of("paramixel.parallelism.p1", "1", "paramixel.parallelism.p2", "1"), action);

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.status().message().orElseThrow()).contains("conflicting order");
    }

    @Test
    @DisplayName("an invalid pool size is reported with its key")
    void invalidPoolSizeReported() {
        var result = run(
                Map.of("paramixel.parallelism.p", "0"),
                Parallel.parallel("p", "p")
                        .child(Step.step("child", context -> {}))
                        .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.status().message().orElseThrow()).contains("paramixel.parallelism.p");
    }

    @Test
    @DisplayName("a valid plan runs and no validation error is reported")
    void validPlanRuns() {
        var result = run(
                Map.of("paramixel.parallelism.p", "2"),
                Parallel.parallel("p", "p")
                        .child(Step.step("child", context -> {}))
                        .build());

        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }
}
