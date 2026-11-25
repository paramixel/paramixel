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

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.paramixel.api.discoveryfixtures.AlphaFixture;
import org.paramixel.api.discoveryfixtures.DiscoveryFixtures;

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
        assertThat(Launcher.execute((String[]) null)).isNotNegative();
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void malformedRunnerFlagExitsAsInvalidConfiguration() {
        DiscoveryFixtures.reset();
        final var previousClass =
                System.setProperty(Configuration.MATCH_CLASS_REGEX, Pattern.quote(AlphaFixture.class.getName()));
        final var previousFlag = System.setProperty(Configuration.FAILURE_ON_SKIP, "false");
        try {
            assertThat(Launcher.execute()).isZero();
            System.setProperty(Configuration.FAILURE_ON_SKIP, "treu");
            assertThat(Launcher.execute()).isEqualTo(2);
        } finally {
            restoreProperty(Configuration.FAILURE_ON_SKIP, previousFlag);
            restoreProperty(Configuration.MATCH_CLASS_REGEX, previousClass);
        }
    }

    private static void restoreProperty(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
