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

/**
 * Command-line launcher contract: help, usage errors, and exit codes without terminating the JVM.
 */
class LauncherTest {

    @Test
    @DisplayName("--help prints usage and succeeds")
    void helpSucceeds() {
        assertThat(Launcher.execute(new String[] {"--help"})).isZero();
        assertThat(Launcher.execute(new String[] {"-h"})).isZero();
    }

    @Test
    @DisplayName("an unknown argument is a usage error")
    void unknownArgumentIsUsageError() {
        assertThat(Launcher.execute(new String[] {"--nope"})).isEqualTo(2);
        assertThat(Launcher.execute(null)).isNotNegative();
    }
}
