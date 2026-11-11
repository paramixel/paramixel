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
import static org.paramixel.api.action.Conditional.conditional;
import static org.paramixel.api.action.Instance.of;
import static org.paramixel.api.action.Isolated.isolated;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Timeout.timeout;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Named builders")
class NamedBuildersTest {

    @Nested
    @DisplayName("scope")
    class ScopeTests {

        @Test
        @DisplayName("returns builder with correct name")
        void returnsBuilderWithCorrectName() {
            var action = scope("test").body(Step.step("body", ctx -> {})).build();
            assertThat(action.displayName()).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("sequential")
    class SequentialTests {

        @Test
        @DisplayName("returns builder with correct default dependent mode")
        void returnsBuilderWithCorrectDefaults() {
            var action = Sequential.sequential("test")
                    .child(Step.step("a", ctx -> {}))
                    .build();
            assertThat(action.displayName()).isEqualTo("test");
            assertThat(action.isDependent()).isTrue();
        }

        @Test
        @DisplayName("accepts children and builds")
        void acceptsChildrenAndBuilds() {
            var action = Sequential.sequential("s")
                    .child(Step.step("a", ctx -> {}))
                    .child(Step.step("b", ctx -> {}))
                    .build();
            assertThat(action.displayName()).isEqualTo("s");
            assertThat(action.children()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("parallel")
    class ParallelTests {

        @Test
        @DisplayName("returns builder with MAX_VALUE parallelism sentinel")
        void returnsBuilderWithDefaults() {
            var action = parallel("test", "t0").child(Step.step("a", ctx -> {})).build();
            assertThat(action.displayName()).isEqualTo("test");
            assertThat(action.pool()).isNotBlank();
        }

        @Test
        @DisplayName("keeps the pool name")
        void keepsPoolName() {
            var action = parallel("p", "p4x1")
                    .child(Step.step("a", ctx -> {}))
                    .child(Step.step("b", ctx -> {}))
                    .build();
            assertThat(action.pool()).startsWith("p4");
            assertThat(action.children()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("conditional")
    class ConditionalTests {

        @Test
        @DisplayName("returns builder with condition and body")
        void returnsBuilderWithConditionAndBody() {
            var body = Step.step("body", ctx -> {});
            var action = conditional("cond", ctx -> true).body(body).build();
            assertThat(action.displayName()).isEqualTo("cond");
            assertThat(action.body()).isSameAs(body);
        }
    }

    @Nested
    @DisplayName("instance")
    class InstanceTests {

        @Test
        @DisplayName("with name and factory creates instance")
        void withNameAndFactoryCreatesInstance() {
            var body = Step.step("body", ctx -> {});
            var action = of("my-fixture", Object.class, () -> new Object())
                    .body(body)
                    .build();
            assertThat(action.displayName()).isEqualTo("my-fixture");
        }

        @Test
        @DisplayName("with class creates instance using simple name")
        void withClassCreatesInstanceUsingSimpleName() {
            var body = Step.step("body", ctx -> {});
            var action = of(Object.class).body(body).build();
            assertThat(action.displayName()).isEqualTo("Object");
        }

        @Test
        @DisplayName("with name and class creates instance")
        void withNameAndClassCreatesInstance() {
            var body = Step.step("body", ctx -> {});
            var action = of("my-fixture", Object.class).body(body).build();
            assertThat(action.displayName()).isEqualTo("my-fixture");
        }
    }

    @Nested
    @DisplayName("isolated")
    class IsolatedTests {

        @Test
        @DisplayName("returns builder with lock name")
        void returnsBuilderWithLockName() {
            var body = Step.step("body", ctx -> {});
            var action = isolated("iso", "db-lock").body(body).build();
            assertThat(action.displayName()).isEqualTo("iso");
            assertThat(action.lockName()).isEqualTo("db-lock");
            assertThat(action.body()).isSameAs(body);
        }
    }

    @Nested
    @DisplayName("timeout")
    class TimeoutTests {

        @Test
        @DisplayName("returns builder with timeout and body")
        void returnsBuilderWithTimeoutAndBody() {
            var body = Step.step("body", ctx -> {});
            var action = timeout("tm").timeout(Duration.ofSeconds(2)).body(body).build();
            assertThat(action.displayName()).isEqualTo("tm");
            assertThat(action.timeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(action.body()).isSameAs(body);
        }
    }

    @Nested
    @DisplayName("loop")
    class LoopTests {

        @Test
        @DisplayName("with maxIterations creates action")
        void withMaxIterationsCreatesAction() {
            var body = Step.step("body", ctx -> {});
            var action = Loop.loop("u").maxIterations(10).body(body).build();
            assertThat(action.displayName()).isEqualTo("u");
            assertThat(action.maxIterations()).isEqualTo(10);
            assertThat(action.body()).isSameAs(body);
        }

        @Test
        @DisplayName("with predicate creates action")
        void withPredicateCreatesAction() {
            var predicate = new java.util.function.Predicate<org.paramixel.api.Context>() {
                @Override
                public boolean test(org.paramixel.api.Context ctx) {
                    return true;
                }
            };
            var body = Step.step("body", ctx -> {});
            var action =
                    Loop.loop("u").maxIterations(10).until(predicate).body(body).build();
            assertThat(action.until().orElseThrow().test(null, org.paramixel.api.Status.PASSED))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("step")
    class StepTests {

        @Test
        @DisplayName("returns step with correct display name")
        void returnsStepWithCorrectName() {
            var action = step("my-step", ctx -> {});
            assertThat(action.displayName()).isEqualTo("my-step");
        }
    }
}
