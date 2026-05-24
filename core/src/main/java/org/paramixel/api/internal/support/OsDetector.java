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

/**
 * Detects the operating system environment for path expansion decisions.
 *
 * <p>If the {@code os.name} system property is absent or unrecognizable, the detector assumes Linux.
 *
 * @see TildePathExpander
 */
interface OsDetector {

    /**
     * Returns whether the current operating system is Windows.
     *
     * @return {@code true} on Windows
     */
    boolean isWindows();

    /**
     * Returns whether the current operating system is macOS.
     *
     * @return {@code true} on macOS
     */
    default boolean isMac() {
        return false;
    }

    /**
     * Returns whether the current operating system is Linux (or any non-Windows, non-macOS Unix).
     *
     * <p>This is the default assumption when the OS is not Windows or macOS.
     *
     * @return {@code true} on Linux or generic Unix
     */
    default boolean isLinux() {
        return !isWindows() && !isMac();
    }
}
