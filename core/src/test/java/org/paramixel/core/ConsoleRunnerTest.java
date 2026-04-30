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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.action.Direct;
import org.paramixel.core.discovery.Selector;

@DisplayName("ConsoleRunner")
class ConsoleRunnerTest {

    @Test
    @DisplayName("returns empty when no action is resolved")
    void returnsEmptyWhenNoActionIsResolved() {
        var selector = Selector.byPackageName("non.existent.package");

        var result = ConsoleRunner.run(selector);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns exit code 0 when no action is resolved")
    void returnsExitCode0WhenNoActionIsResolved() {
        var selector = Selector.byPackageName("non.existent.package");

        int exitCode = ConsoleRunner.runAndReturnExitCode(selector);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("run returns result with PASS status for passing action")
    void runReturnsPassResultForPassingAction() {
        Action action = org.paramixel.core.action.Noop.of("passing");

        Result result = ConsoleRunner.run(action);

        assertThat(result.getStatus().isPass()).isTrue();
    }

    @Test
    @DisplayName("runAndReturnExitCode returns 0 for passing action")
    void runAndReturnExitCodeReturns0ForPassingAction() {
        Action action = org.paramixel.core.action.Noop.of("passing");

        int exitCode = ConsoleRunner.runAndReturnExitCode(action);

        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("runAndReturnExitCode returns 1 for failing action")
    void runAndReturnExitCodeReturns1ForFailingAction() {
        Action action = Direct.of("failing", context -> {
            throw new RuntimeException("test failure");
        });

        int exitCode = ConsoleRunner.runAndReturnExitCode(action);

        assertThat(exitCode).isEqualTo(1);
    }
}
