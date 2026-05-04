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

package org.paramixel.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.exception.SkipException;

@DisplayName("Runner convenience methods")
class RunnerConvenienceTest {

    @Nested
    @DisplayName("run(Selector)")
    class RunSelector {

        @Test
        @DisplayName("returns empty when no action is resolved")
        void returnsEmptyWhenNoActionIsResolved() {
            var selector = Selector.builder()
                    .packageMatch("^non\\.existent\\.package$")
                    .build();

            var result = Factory.defaultRunner().run(selector);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("rejects null selector")
        void rejectsNullSelector() {
            assertThatThrownBy(() -> Factory.defaultRunner().run((Selector) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("selector must not be null");
        }
    }

    @Nested
    @DisplayName("runAndReturnExitCode(Selector)")
    class RunAndReturnExitCodeSelector {

        @Test
        @DisplayName("returns exit code 0 when no action is resolved")
        void returnsExitCode0WhenNoActionIsResolved() {
            var selector = Selector.builder()
                    .packageMatch("^non\\.existent\\.package$")
                    .build();

            int exitCode = Factory.defaultRunner().runAndReturnExitCode(selector);

            assertThat(exitCode).isZero();
        }

        @Test
        @DisplayName("rejects null selector")
        void rejectsNullSelector() {
            assertThatThrownBy(() -> Factory.defaultRunner().runAndReturnExitCode((Selector) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("selector must not be null");
        }
    }

    @Nested
    @DisplayName("run(Selector) uses runner configuration")
    class RunSelectorUsesConfiguration {

        @Test
        @DisplayName("applies configuration tag filter during discovery")
        void appliesConfigurationTagFilterDuringDiscovery() {
            Runner runner = Runner.builder()
                    .configuration(Map.of(
                            Configuration.CLASS_MATCH, "RunnerTaggedFixture",
                            Configuration.TAG_MATCH, "^smoke$"))
                    .build();
            var selector = Selector.builder().classMatch("RunnerTaggedFixture").build();

            var result = runner.run(selector);

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getStatus().isPass()).isTrue();
        }
    }

    @Nested
    @DisplayName("run(Action)")
    class RunAction {

        @Test
        @DisplayName("returns result with PASS status for passing action")
        void runReturnsPassResultForPassingAction() {
            Action action = Noop.of("passing");

            Result result = Factory.defaultRunner().run(action);

            assertThat(result.getStatus().isPass()).isTrue();
        }

        @Test
        @DisplayName("returns result with SKIP status for skipped action")
        void runReturnsSkipResultForSkippedAction() {
            Action action = Direct.of("skipped", context -> SkipException.skip());

            Result result = Factory.defaultRunner().run(action);

            assertThat(result.getStatus().isSkip()).isTrue();
        }

        @Test
        @DisplayName("runAndReturnExitCode rejects null action")
        void runAndReturnExitCodeRejectsNullAction() {
            assertThatThrownBy(() -> Factory.defaultRunner().runAndReturnExitCode((Action) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("action must not be null");
        }
    }

    @Nested
    @DisplayName("runAndReturnExitCode(Action)")
    class RunAndReturnExitCodeAction {

        @Test
        @DisplayName("returns 0 for passing action")
        void returns0ForPassingAction() {
            Action action = Noop.of("passing");

            int exitCode = Factory.defaultRunner().runAndReturnExitCode(action);

            assertThat(exitCode).isZero();
        }

        @Test
        @DisplayName("returns 0 for skipped action")
        void returns0ForSkippedAction() {
            Action action = Direct.of("skipped", context -> SkipException.skip());

            int exitCode = Factory.defaultRunner().runAndReturnExitCode(action);

            assertThat(exitCode).isZero();
        }

        @Test
        @DisplayName("returns 1 for failing action")
        void returns1ForFailingAction() {
            Action action = Direct.of("failing", context -> {
                throw new RuntimeException("test failure");
            });

            int exitCode = Factory.defaultRunner().runAndReturnExitCode(action);

            assertThat(exitCode).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("runAndReturnExitCode(Action) with failure-on-skip")
    class RunAndReturnExitCodeActionFailureOnSkip {

        @Test
        @DisplayName("returns 1 for skipped action when FAILURE_ON_SKIP is true")
        void returns1ForSkippedActionWhenFailureOnSkipIsTrue() {
            Action action = Direct.of("skipped", context -> SkipException.skip());
            Runner runner = Runner.builder()
                    .configuration(Map.of(Configuration.FAILURE_ON_SKIP, "true"))
                    .build();

            int exitCode = runner.runAndReturnExitCode(action);

            assertThat(exitCode).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("runAndReturnExitCode(Selector) with results")
    class RunAndReturnExitCodeSelectorResults {

        @Test
        @DisplayName("returns 0 for passing resolved action")
        void returns0ForPassingResolvedAction() {
            var selector = Selector.builder().classMatch("ResolverSmokeFixture").build();

            int exitCode = Factory.defaultRunner().runAndReturnExitCode(selector);

            assertThat(exitCode).isZero();
        }
    }

    @Nested
    @DisplayName("runAndExit")
    class RunAndExit {

        @Test
        @DisplayName("runAndExit(Action) rejects null action")
        void runAndExitActionRejectsNullAction() {
            assertThatThrownBy(() -> Factory.defaultRunner().runAndExit((Action) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("runAndExit(Selector) rejects null selector")
        void runAndExitSelectorRejectsNullSelector() {
            assertThatThrownBy(() -> Factory.defaultRunner().runAndExit((Selector) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
