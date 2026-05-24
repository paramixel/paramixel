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

package org.paramixel.api.internal.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConcreteOsDetector")
class ConcreteOsDetectorTest {

    private final ConcreteOsDetector detector = new ConcreteOsDetector();

    @Test
    @DisplayName("isWindows returns consistent value for current OS")
    void isWindowsReturnsConsistentValue() {
        boolean isWindows = System.getProperty("os.name", "linux").toLowerCase().contains("win");
        assertThat(detector.isWindows()).isEqualTo(isWindows);
    }

    @Test
    @DisplayName("isMac returns consistent value for current OS")
    void isMacReturnsConsistentValue() {
        boolean isMac = System.getProperty("os.name", "linux").toLowerCase().contains("mac");
        assertThat(detector.isMac()).isEqualTo(isMac);
    }

    @Test
    @DisplayName("isLinux returns true when not Windows and not Mac")
    void isLinuxReturnsTrueWhenNotWindowsAndNotMac() {
        boolean isLinux = !detector.isWindows() && !detector.isMac();
        assertThat(detector.isLinux()).isEqualTo(isLinux);
    }
}
