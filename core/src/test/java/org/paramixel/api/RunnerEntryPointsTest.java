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

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Step;
import org.paramixel.api.selector.Selector;

@DisplayName("Runner entry points")
class RunnerEntryPointsTest {

    @Test
    @DisplayName("a selector with no matches returns a result with an empty descriptor")
    void selectorWithNoMatchesReturnsEmptyDescriptor() {
        var runner = Runner.builder().listener(new NoOpListener()).build();
        var result = runner.run(Selector.builder()
                .classPattern(Pattern.compile("nonexistent.ClassName"))
                .build());

        assertThat(result.descriptor()).isEmpty();
        assertThat(result.status().kind()).isEqualTo(Status.Kind.SKIPPED);
        assertThat(result.exitCode()).isZero();
    }

    @Test
    @DisplayName("a selector with no matches fails when failIfNoTests is set")
    void selectorWithNoMatchesFailsWhenFailIfNoTests() {
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.FAIL_IF_NO_TESTS, "true")))
                .listener(new NoOpListener())
                .build();
        var result = runner.run(Selector.builder()
                .classPattern(Pattern.compile("nonexistent.ClassName"))
                .build());

        assertThat(result.descriptor()).isEmpty();
        assertThat(result.status().kind()).isEqualTo(Status.Kind.FAILED);
        assertThat(result.exitCode()).isOne();
    }

    @Test
    @DisplayName("run(Action) returns a result with a present descriptor")
    void directActionRunReturnsDescriptor() {
        var runner = Runner.builder().listener(new NoOpListener()).build();

        var result = runner.run(Step.step("test", context -> {}));

        assertThat(result.descriptor()).isPresent();
        assertThat(result.status().kind()).isEqualTo(Status.Kind.PASSED);
    }

    @Test
    @DisplayName("run() applies the configured selector")
    void runAppliesConfiguredSelector() {
        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.MATCH_CLASS_REGEX, "NoSuchClassXyz")))
                .listener(new NoOpListener())
                .build();

        var result = runner.run();

        assertThat(result.descriptor()).isEmpty();
    }

    private static final class NoOpListener implements Listener {

        @Override
        public void onRunStarted(final Configuration configuration) {}

        @Override
        public void onDiscoveryStarted() {}

        @Override
        public void onDiscoveryCompleted(final Optional<Descriptor> root) {}

        @Override
        public void onBeforeExecution(final Descriptor descriptor) {}

        @Override
        public void onAfterExecution(final Descriptor descriptor) {}

        @Override
        public void onRunCompleted(final Result result) {}
    }
}
