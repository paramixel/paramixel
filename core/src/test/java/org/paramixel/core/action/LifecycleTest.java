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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.SkipException;

@DisplayName("Lifecycle")
class LifecycleTest {

    @Test
    @DisplayName("of(name, body) creates body-only lifecycle that passes")
    void ofNameBodyCreatesBodyOnlyLifecycleThatPasses() {
        Action body = Noop.of("body");
        Lifecycle lifecycle = Lifecycle.of("lifecycle", body);

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.failure()).isEmpty();
        assertThat(lifecycle.setup()).isEmpty();
        assertThat(lifecycle.body()).isSameAs(body);
        assertThat(lifecycle.teardown()).isEmpty();
        assertThat(lifecycle.children()).containsExactly(body);
    }

    @Test
    @DisplayName("of(name, setup, body) creates setup-body lifecycle that passes")
    void ofNameSetupBodyCreatesSetupBodyLifecycleThatPasses() {
        var setupRan = new AtomicBoolean();
        Action body = Noop.of("body");
        var lifecycle = Lifecycle.of("lifecycle", context -> setupRan.set(true), body);

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.failure()).isEmpty();
        assertThat(setupRan).isTrue();
        assertThat(lifecycle.setup()).isPresent();
        assertThat(lifecycle.body()).isSameAs(body);
        assertThat(lifecycle.teardown()).isEmpty();
    }

    @Test
    @DisplayName("of(name, body, teardown) creates body-teardown lifecycle that passes")
    void ofNameBodyTeardownCreatesBodyTeardownLifecycleThatPasses() {
        var teardownRan = new AtomicBoolean();
        Action body = Noop.of("body");
        var lifecycle = Lifecycle.of("lifecycle", body, context -> teardownRan.set(true));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.failure()).isEmpty();
        assertThat(teardownRan).isTrue();
        assertThat(lifecycle.setup()).isEmpty();
        assertThat(lifecycle.body()).isSameAs(body);
        assertThat(lifecycle.teardown()).isPresent();
    }

    @Test
    @DisplayName("of(name, setup, body, teardown) creates full lifecycle that passes")
    void ofNameSetupBodyTeardownCreatesFullLifecycleThatPasses() {
        var setupRan = new AtomicBoolean();
        var teardownRan = new AtomicBoolean();
        Action body = Noop.of("body");
        var lifecycle =
                Lifecycle.of("lifecycle", context -> setupRan.set(true), body, context -> teardownRan.set(true));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(result.failure()).isEmpty();
        assertThat(setupRan).isTrue();
        assertThat(teardownRan).isTrue();
        assertThat(lifecycle.setup()).isPresent();
        assertThat(lifecycle.body()).isSameAs(body);
        assertThat(lifecycle.teardown()).isPresent();
    }

    @Test
    @DisplayName("setup failure prevents body execution but runs teardown")
    void setupFailurePreventsBodyExecutionButRunsTeardown() {
        var setupException = new RuntimeException("setup failed");
        var bodyRan = new AtomicBoolean();
        var teardownRan = new AtomicBoolean();
        Action body = Direct.of("body", context -> bodyRan.set(true));
        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                context -> {
                    throw setupException;
                },
                body,
                context -> teardownRan.set(true));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.failure()).isPresent().get().isSameAs(setupException);
        assertThat(result.failure().get().getSuppressed()).isEmpty();
        assertThat(bodyRan).isFalse();
        assertThat(teardownRan).isTrue();
        assertThat(result.children()).hasSize(1);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.SKIP);
        assertThat(result.children().get(0).action()).isSameAs(body);
    }

    @Test
    @DisplayName("setup SkipException skips body but runs teardown")
    void setupSkipExceptionSkipsBodyButRunsTeardown() {
        var skipException = new SkipException("setup skipped");
        var bodyRan = new AtomicBoolean();
        var teardownRan = new AtomicBoolean();
        Action body = Direct.of("body", context -> bodyRan.set(true));
        Lifecycle lifecycle = Lifecycle.of(
                "lifecycle",
                context -> {
                    throw skipException;
                },
                body,
                context -> teardownRan.set(true));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.SKIP);
        assertThat(result.failure()).isPresent().get().isSameAs(skipException);
        assertThat(bodyRan).isFalse();
        assertThat(teardownRan).isTrue();
        assertThat(result.children()).hasSize(1);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.SKIP);
        assertThat(result.children().get(0).action()).isSameAs(body);
    }

    @Test
    @DisplayName("body failure while setup and teardown pass fails with body exception")
    void bodyFailureWhileSetupAndTeardownPassFailsWithBodyException() {
        var bodyException = new RuntimeException("body failed");
        var setupRan = new AtomicBoolean();
        var teardownRan = new AtomicBoolean();
        Action body = Direct.of("body", context -> {
            throw bodyException;
        });
        Lifecycle lifecycle =
                Lifecycle.of("lifecycle", context -> setupRan.set(true), body, context -> teardownRan.set(true));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.failure()).isPresent().get().isSameAs(bodyException);
        assertThat(result.failure().get().getSuppressed()).isEmpty();
        assertThat(setupRan).isTrue();
        assertThat(teardownRan).isTrue();
        assertThat(result.children()).hasSize(1);
        assertThat(result.children().get(0).status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.children().get(0).action()).isSameAs(body);
    }

    @Test
    @DisplayName("teardown failure after body pass fails with teardown exception")
    void teardownFailureAfterBodyPassFailsWithTeardownException() {
        var teardownException = new RuntimeException("teardown failed");
        var setupRan = new AtomicBoolean();
        Action body = Noop.of("body");
        var lifecycle = Lifecycle.of("lifecycle", context -> setupRan.set(true), body, context -> {
            throw teardownException;
        });

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.failure()).isPresent().get().isSameAs(teardownException);
        assertThat(result.failure().get().getSuppressed()).isEmpty();
        assertThat(setupRan).isTrue();
    }

    @Test
    @DisplayName("body and teardown both fail - primary failure is body, teardown is suppressed")
    void bodyAndTeardownBothFailPrimaryFailureIsBodyTeardownIsSuppressed() {
        var bodyException = new RuntimeException("body failed");
        var teardownException = new RuntimeException("teardown failed");
        var setupRan = new AtomicBoolean();
        Action body = Direct.of("body", context -> {
            throw bodyException;
        });
        Lifecycle lifecycle = Lifecycle.of("lifecycle", context -> setupRan.set(true), body, context -> {
            throw teardownException;
        });

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(result.failure()).isPresent().get().isSameAs(bodyException);
        assertThat(result.failure().get().getSuppressed()).hasSize(1);
        assertThat(result.failure().get().getSuppressed()[0]).isSameAs(teardownException);
        assertThat(setupRan).isTrue();
    }

    @Test
    @DisplayName("accessors return correct values")
    void accessorsReturnCorrectValues() {
        var setup = Executable.noop();
        Action body = Noop.of("body");
        var teardown = Executable.noop();
        var lifecycle = Lifecycle.of("lifecycle", setup, body, teardown);

        assertThat(lifecycle.setup()).isPresent().get().isSameAs(setup);
        assertThat(lifecycle.body()).isSameAs(body);
        assertThat(lifecycle.teardown()).isPresent().get().isSameAs(teardown);
        assertThat(lifecycle.children()).containsExactly(body);
    }

    @Test
    @DisplayName("of(name, body) rejects null body")
    void ofNameBodyRejectsNullBody() {
        assertThatNullPointerException()
                .isThrownBy(() -> Lifecycle.of("lifecycle", (Action) null))
                .withMessage("body must not be null");
    }

    @Test
    @DisplayName("of(name, setup, body) rejects null body")
    void ofNameSetupBodyRejectsNullBody() {
        assertThatNullPointerException()
                .isThrownBy(() -> Lifecycle.of("lifecycle", Executable.noop(), (Action) null))
                .withMessage("body must not be null");
    }

    @Test
    @DisplayName("of(name, body, teardown) rejects null body")
    void ofNameBodyTeardownRejectsNullBody() {
        assertThatNullPointerException()
                .isThrownBy(() -> Lifecycle.of("lifecycle", (Action) null, Executable.noop()))
                .withMessage("body must not be null");
    }

    @Test
    @DisplayName("of(name, setup, body, teardown) rejects null body")
    void ofNameSetupBodyTeardownRejectsNullBody() {
        assertThatNullPointerException()
                .isThrownBy(() -> Lifecycle.of("lifecycle", Executable.noop(), (Action) null, Executable.noop()))
                .withMessage("body must not be null");
    }

    @Test
    @DisplayName("setup and teardown counters increment correctly in nested lifecycle")
    void setupAndTeardownCountersIncrementCorrectlyInNestedLifecycle() {
        var outerSetup = new AtomicInteger();
        var outerTeardown = new AtomicInteger();
        var innerSetup = new AtomicInteger();
        var innerTeardown = new AtomicInteger();
        var bodyExecutions = new AtomicInteger();

        Action innerBody = Direct.of("inner-body", context -> bodyExecutions.incrementAndGet());
        Lifecycle inner = Lifecycle.of(
                "inner",
                context -> innerSetup.incrementAndGet(),
                innerBody,
                context -> innerTeardown.incrementAndGet());

        Action outerBody = Lifecycle.of("outer-body", context -> {}, inner);
        Lifecycle outer = Lifecycle.of(
                "outer",
                context -> outerSetup.incrementAndGet(),
                outerBody,
                context -> outerTeardown.incrementAndGet());

        Result result = Runner.builder().build().run(outer);

        assertThat(result.status()).isEqualTo(Result.Status.PASS);
        assertThat(outerSetup.get()).isEqualTo(1);
        assertThat(outerTeardown.get()).isEqualTo(1);
        assertThat(innerSetup.get()).isEqualTo(1);
        assertThat(innerTeardown.get()).isEqualTo(1);
        assertThat(bodyExecutions.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("teardown runs even when setup throws SkipException")
    void teardownRunsEvenWhenSetupThrowsSkipException() {
        var teardownRan = new AtomicBoolean();
        var lifecycle = Lifecycle.of(
                "lifecycle",
                context -> {
                    throw new SkipException("skip");
                },
                Noop.of("body"),
                context -> teardownRan.set(true));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.SKIP);
        assertThat(teardownRan).isTrue();
    }

    @Test
    @DisplayName("teardown runs even when setup throws exception")
    void teardownRunsEvenWhenSetupThrowsException() {
        var teardownRan = new AtomicBoolean();
        var lifecycle = Lifecycle.of(
                "lifecycle",
                context -> {
                    throw new RuntimeException("setup failed");
                },
                Noop.of("body"),
                context -> teardownRan.set(true));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(teardownRan).isTrue();
    }

    @Test
    @DisplayName("teardown runs even when body fails")
    void teardownRunsEvenWhenBodyFails() {
        var teardownRan = new AtomicBoolean();
        var lifecycle = Lifecycle.of(
                "lifecycle",
                Executable.noop(),
                Direct.of("body", context -> {
                    throw new RuntimeException("body failed");
                }),
                context -> teardownRan.set(true));

        Result result = Runner.builder().build().run(lifecycle);

        assertThat(result.status()).isEqualTo(Result.Status.FAIL);
        assertThat(teardownRan).isTrue();
    }
}
