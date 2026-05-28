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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Lifecycle;
import org.paramixel.api.action.Spec;
import org.paramixel.api.action.Step;

@DisplayName("Runner Builder overloads")
class RunnerBuilderOverloadTest {

    @Test
    @DisplayName("run(Spec<?>) resolves and runs a Lifecycle spec")
    void runSpecResolvesAndRunsLifecycle() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        Spec<?> spec = Lifecycle.of("lifecycle").before("step", obj -> {});

        Result result = runner.run(spec);

        assertThat(result.descriptor()).isPresent();
        assertThat(result.status()).isEqualTo(Status.PASSED);
    }

    @Test
    @DisplayName("run(Spec<?>) returns same result for pre-built action passed as spec")
    void runSpecWithPreBuiltAction() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        Step<?> step = Step.of("step", obj -> {});

        Result specResult = runner.run((Spec<?>) step);
        Result actionResult = runner.run(step);

        assertThat(specResult.status()).isEqualTo(actionResult.status());
    }

    @Test
    @DisplayName("runAndReturnExitCode(Spec<?>) resolves and returns exit code for passing action")
    void runAndReturnExitCodeSpecReturnsZeroForPassingAction() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        Spec<?> spec = Lifecycle.of("lifecycle").before("step", obj -> {});

        int exitCode = runner.runAndReturnExitCode(spec);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("runAndReturnExitCode(Spec<?>) returns non-zero for failing action")
    void runAndReturnExitCodeSpecReturnsNonZeroForFailingAction() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        org.paramixel.api.action.Spec<?> spec = Lifecycle.of("lifecycle").before("failing", obj -> {
            throw new RuntimeException("failure");
        });

        int exitCode = runner.runAndReturnExitCode(spec);

        assertThat(exitCode).isNotZero();
    }

    @Test
    @DisplayName("runAndReturnExitCode(Spec<?>) returns same exit code for pre-built action and spec overload")
    void runAndReturnExitCodeSpecWithPreBuiltAction() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        Step<?> step = Step.of("step", obj -> {});

        int specCode = runner.runAndReturnExitCode((Spec<?>) step);
        int actionCode = runner.runAndReturnExitCode(step);

        assertThat(specCode).isEqualTo(actionCode);
    }

    @Test
    @DisplayName("run(Spec<?>) with accumulating spec consumes it on first call")
    void runSpecConsumesAccumulatingSpec() {
        var listener = new NoOpListener();
        Runner runner = Runner.builder().listener(listener).build();
        var spec = Lifecycle.of("lifecycle").child("step", obj -> {});

        runner.run(spec);

        assertThatThrownBy(spec::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("spec already resolved");
    }

    private static final class NoOpListener implements Listener {

        @Override
        public void initialize(final Configuration configuration) {}

        @Override
        public void onDiscoveryStarted() {}

        @Override
        public void onRunStarted() {}

        @Override
        public void onDiscoveryCompleted(final Descriptor root) {}

        @Override
        public void onBeforeExecution(final Descriptor descriptor) {}

        @Override
        public void onAfterExecution(final Descriptor descriptor) {}

        @Override
        public void onRunCompleted(final Result result) {}
    }
}
