/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.util;

/**
 * Utility for detecting Java version features at runtime.
 *
 * <p>This utility provides methods to detect Java version compatibility
 * for features that vary across Java versions, such as virtual threads.
 *
 * <p><b>Thread safety</b>
 * <p>This class is thread-safe.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class JavaVersionUtil {

    /**
     * The major version of the current Java runtime.
     *
     * This value is computed once at class loading for performance.
     */
    private static final int CURRENT_JAVA_VERSION = getCurrentJavaVersion();

    /**
     * Private constructor to prevent instantiation.
     */
    private JavaVersionUtil() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Returns {@code true} if the current Java runtime supports virtual threads.
     *
     * <p>Virtual threads are supported in Java 21 and later.
     *
     * @return {@code true} if virtual threads are supported, {@code false} otherwise
     */
    public static boolean supportsVirtualThreads() {
        return CURRENT_JAVA_VERSION >= 21;
    }

    /**
     * Returns {@code true} if the current Java runtime is at least Java 17.
     *
     * @return {@code true} if Java 17 or later is available
     */
    public static boolean isJava17OrLater() {
        return CURRENT_JAVA_VERSION >= 17;
    }

    /**
     * Returns the major version of the current Java runtime.
     *
     * @return the major version number (e.g., 21 for Java 21)
     */
    public static int getCurrentJavaVersion() {
        // The version string format changed in Java 9
        final String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            // Java 8 and earlier: "1.8.0_292" -> return 8
            return Integer.parseInt(version.substring(2, 3));
        } else {
            // Java 9 and later: "17.0.1" or "21" -> return 17 or 21
            final int dotIndex = version.indexOf('.');
            if (dotIndex != -1) {
                return Integer.parseInt(version.substring(0, dotIndex));
            }
            return Integer.parseInt(version);
        }
    }

    /**
     * Returns a human-readable Java version string for logging and diagnostics.
     *
     * @return the full Java version string
     */
    public static String getJavaVersionString() {
        return System.getProperty("java.version");
    }
}
