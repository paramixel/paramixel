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
}
