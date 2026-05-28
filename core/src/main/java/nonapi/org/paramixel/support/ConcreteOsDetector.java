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

package nonapi.org.paramixel.support;

import java.util.Locale;
import java.util.Objects;

/**
 * Detects the current operating system by inspecting the {@code os.name} system property.
 *
 * <p>Falls back to {@code "linux"} when the property is absent, so that Linux is assumed
 * by default — consistent with the contract defined by {@link OsDetector#isLinux()}.
 *
 * @see OsDetector
 * @see TildePathExpander
 */
final class ConcreteOsDetector implements OsDetector {

    private static final String OS_NAME =
            Objects.requireNonNullElse(System.getProperty("os.name"), "linux").toLowerCase(Locale.ROOT);

    @Override
    public boolean isWindows() {
        return OS_NAME.contains("win");
    }

    @Override
    public boolean isMac() {
        return OS_NAME.contains("mac");
    }

    @Override
    public boolean isLinux() {
        return !isWindows() && !isMac();
    }
}
