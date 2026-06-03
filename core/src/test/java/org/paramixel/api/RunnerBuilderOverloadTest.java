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

package org.paramixel.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Scope;
import org.paramixel.api.action.Step;

@DisplayName("Runner Builder overloads")
class RunnerBuilderOverloadTest {

    @Test
    @DisplayName("run(Action) resolves and runs a Lifecycle action")
    void runActionResolvesAndRunsLifecycle() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        Action action =
                Scope.builder("lifecycle").body(Step.of("step", context -> {})).build();

        Result result = runner.run(action);

        assertThat(result.descriptor()).isPresent();
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("run(Action) returns same result for pre-built action")
    void runActionWithPreBuiltAction() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        Step step = Step.of("step", context -> {});

        Result result = runner.run(step);

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("runAndReturnExitCode(Action) resolves and returns exit code for passing action")
    void runAndReturnExitCodeActionReturnsZeroForPassingAction() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        Action action =
                Scope.builder("lifecycle").body(Step.of("step", context -> {})).build();

        int exitCode = runner.runAndReturnExitCode(action);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("runAndReturnExitCode(Action) returns non-zero for failing action")
    void runAndReturnExitCodeActionReturnsNonZeroForFailingAction() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        Action action = Scope.builder("lifecycle")
                .body(Step.of("failing", context -> {
                    throw new RuntimeException("failure");
                }))
                .build();

        int exitCode = runner.runAndReturnExitCode(action);

        assertThat(exitCode).isNotZero();
    }

    @Test
    @DisplayName("run(Action) with accumulating builder leaves builder reusable")
    void runActionLeavesAccumulatingBuilderReusable() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        var builder = Scope.builder("lifecycle").body(Step.of("step", context -> {}));

        runner.run(builder.build());

        assertThat(builder.build().body()).isNotNull();
    }

    private static final class NoOpListener implements Listener {

        @Override
        public void initialize(final Configuration configuration) {}

        @Override
        public void onDiscoveryStarted() {}

        @Override
        public void onDiscoveryCompleted(final Descriptor root) {}

        @Override
        public void onRunStarted() {}

        @Override
        public void onBeforeExecution(final Descriptor descriptor) {}

        @Override
        public void onAfterExecution(final Descriptor descriptor) {}

        @Override
        public void onRunCompleted(final Result result) {}
    }
}
