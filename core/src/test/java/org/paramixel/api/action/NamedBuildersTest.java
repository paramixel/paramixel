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
import static org.paramixel.api.action.Assert.assertFalse;
import static org.paramixel.api.action.Assert.assertThat;
import static org.paramixel.api.action.Assert.assertTrue;
import static org.paramixel.api.action.Conditional.conditional;
import static org.paramixel.api.action.Delay.delay;
import static org.paramixel.api.action.Delay.delayRandom;
import static org.paramixel.api.action.Instance.instance;
import static org.paramixel.api.action.Isolated.isolated;
import static org.paramixel.api.action.Parallel.parallel;
import static org.paramixel.api.action.Repeat.repeat;
import static org.paramixel.api.action.Scope.scope;
import static org.paramixel.api.action.Sequence.sequence;
import static org.paramixel.api.action.Step.step;
import static org.paramixel.api.action.Timeout.timeout;
import static org.paramixel.api.action.Until.until;

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
            var action = scope("test").body(Step.of("body", ctx -> {})).build();
            assertThat(action.displayName()).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("sequence")
    class SequenceTests {

        @Test
        @DisplayName("returns builder with correct default dependent mode")
        void returnsBuilderWithCorrectDefaults() {
            var action = sequence("test").child(Step.of("a", ctx -> {})).build();
            assertThat(action.displayName()).isEqualTo("test");
            assertThat(action.isDependent()).isTrue();
        }

        @Test
        @DisplayName("accepts children and builds")
        void acceptsChildrenAndBuilds() {
            var action = sequence("s")
                    .child(Step.of("a", ctx -> {}))
                    .child(Step.of("b", ctx -> {}))
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
            var action = parallel("test").child(Step.of("a", ctx -> {})).build();
            assertThat(action.displayName()).isEqualTo("test");
            assertThat(action.parallelism()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("accepts parallelism configuration")
        void acceptsParallelismConfiguration() {
            var action = parallel("p")
                    .parallelism(4)
                    .child(Step.of("a", ctx -> {}))
                    .child(Step.of("b", ctx -> {}))
                    .build();
            assertThat(action.parallelism()).isEqualTo(4);
            assertThat(action.children()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("repeat")
    class RepeatTests {

        @Test
        @DisplayName("returns builder with iterations and body")
        void returnsBuilderWithIterationsAndBody() {
            var body = Step.of("body", ctx -> {});
            var action = repeat("rep").body(body).iterations(5).build();
            assertThat(action.displayName()).isEqualTo("rep");
            assertThat(action.iterations()).isEqualTo(5);
            assertThat(action.body()).isSameAs(body);
        }
    }

    @Nested
    @DisplayName("conditional")
    class ConditionalTests {

        @Test
        @DisplayName("returns builder with condition and body")
        void returnsBuilderWithConditionAndBody() {
            var body = Step.of("body", ctx -> {});
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
            var body = Step.of("body", ctx -> {});
            var action = instance("my-fixture", () -> new Object()).body(body).build();
            assertThat(action.displayName()).isEqualTo("my-fixture");
        }

        @Test
        @DisplayName("with class creates instance using simple name")
        void withClassCreatesInstanceUsingSimpleName() {
            var body = Step.of("body", ctx -> {});
            var action = instance(Object.class).body(body).build();
            assertThat(action.displayName()).isEqualTo("Object");
        }

        @Test
        @DisplayName("with name and class creates instance")
        void withNameAndClassCreatesInstance() {
            var body = Step.of("body", ctx -> {});
            var action = instance("my-fixture", Object.class).body(body).build();
            assertThat(action.displayName()).isEqualTo("my-fixture");
        }
    }

    @Nested
    @DisplayName("isolated")
    class IsolatedTests {

        @Test
        @DisplayName("returns builder with lock name")
        void returnsBuilderWithLockName() {
            var body = Step.of("body", ctx -> {});
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
            var body = Step.of("body", ctx -> {});
            var action = timeout("tm").timeout(Duration.ofSeconds(2)).body(body).build();
            assertThat(action.displayName()).isEqualTo("tm");
            assertThat(action.timeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(action.body()).isSameAs(body);
        }

        @Test
        @DisplayName("accepts milliseconds")
        void acceptsMillis() {
            var body = Step.of("body", ctx -> {});
            var action = timeout("tm").timeoutMillis(3000).body(body).build();
            assertThat(action.timeout()).isEqualTo(Duration.ofMillis(3000));
        }
    }

    @Nested
    @DisplayName("until")
    class UntilTests {

        @Test
        @DisplayName("with maxIterations creates action")
        void withMaxIterationsCreatesAction() {
            var body = Step.of("body", ctx -> {});
            var action = until("u").maxIterations(10).body(body).build();
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
            var body = Step.of("body", ctx -> {});
            var action =
                    until("u").maxIterations(10).until(predicate).body(body).build();
            assertThat(action.until()).containsSame(predicate);
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

    @Nested
    @DisplayName("delay")
    class DelayTests {

        @Test
        @DisplayName("delay with milliseconds creates fixed delay")
        void delayWithMillis() {
            var action = delay("pause", 500);
            assertThat(action.displayName()).isEqualTo("pause");
        }

        @Test
        @DisplayName("delay with duration creates fixed delay")
        void delayWithDuration() {
            var action = delay("pause", Duration.ofSeconds(2));
            assertThat(action.displayName()).isEqualTo("pause");
        }

        @Test
        @DisplayName("delayRandom creates random delay")
        void delayRandomCreatesRandomDelay() {
            var action = delayRandom("pause", 100, 500);
            assertThat(action.displayName()).isEqualTo("pause");
        }
    }

    @Nested
    @DisplayName("assertThat / assertTrue / assertFalse")
    class AssertTests {

        @Test
        @DisplayName("assertThat with boolean values")
        void assertThatBoolean() {
            var action = assertThat("check", true, true);
            assertThat(action.displayName()).isEqualTo("check");
        }

        @Test
        @DisplayName("assertThat with boolean and message")
        void assertThatBooleanWithMessage() {
            var action = assertThat("check", true, true, "msg");
            assertThat(action.displayName()).isEqualTo("check");
        }

        @Test
        @DisplayName("assertThat with supplier")
        void assertThatSupplier() {
            java.util.function.BooleanSupplier supplier = () -> true;
            var action = assertThat("check", true, supplier);
            assertThat(action.displayName()).isEqualTo("check");
        }

        @Test
        @DisplayName("assertThat with supplier and message")
        void assertThatSupplierWithMessage() {
            java.util.function.BooleanSupplier supplier = () -> true;
            var action = assertThat("check", true, supplier, "msg");
            assertThat(action.displayName()).isEqualTo("check");
        }

        @Test
        @DisplayName("assertTrue with boolean")
        void assertTrueBoolean() {
            var action = assertTrue("check", true);
            assertThat(action.displayName()).isEqualTo("check");
        }

        @Test
        @DisplayName("assertTrue with supplier")
        void assertTrueSupplier() {
            var action = assertTrue("check", () -> true);
            assertThat(action.displayName()).isEqualTo("check");
        }

        @Test
        @DisplayName("assertFalse with boolean")
        void assertFalseBoolean() {
            var action = assertFalse("check", false);
            assertThat(action.displayName()).isEqualTo("check");
        }

        @Test
        @DisplayName("assertFalse with supplier")
        void assertFalseSupplier() {
            var action = assertFalse("check", () -> false);
            assertThat(action.displayName()).isEqualTo("check");
        }
    }
}
