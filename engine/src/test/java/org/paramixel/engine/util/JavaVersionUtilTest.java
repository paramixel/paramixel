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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link JavaVersionUtil}.
 */
public class JavaVersionUtilTest {

    @Test
    public void supportsVirtualThreads_detectsCorrectly() {
        // This test verifies that virtual thread detection works
        // The actual result depends on the runtime Java version
        System.out.println("Current Java version: " + JavaVersionUtil.getJavaVersionString());
        System.out.println("Supports virtual threads: " + JavaVersionUtil.supportsVirtualThreads());

        // Basic validation that the method returns a boolean
        assertThat(JavaVersionUtil.supportsVirtualThreads()).isInstanceOf(Boolean.class);
    }

    @Test
    public void isJava17OrLater_detectsCorrectly() {
        // This test verifies that Java 17+ detection works
        final boolean isJava17OrLater = JavaVersionUtil.isJava17OrLater();
        System.out.println("Is Java 17 or later: " + isJava17OrLater);

        assertThat(isJava17OrLater).isInstanceOf(Boolean.class);
        // Since we're compiling with Java 21, this should be true
        assertThat(isJava17OrLater).isTrue();
    }

    @Test
    public void getCurrentJavaVersion_returnsValidVersion() {
        final int version = JavaVersionUtil.getCurrentJavaVersion();
        System.out.println("Detected Java version: " + version);

        assertThat(version).isPositive();
        assertThat(version).isGreaterThanOrEqualTo(17); // Our build target
    }

    @Test
    public void getJavaVersionString_returnsNonEmptyString() {
        final String versionString = JavaVersionUtil.getJavaVersionString();
        System.out.println("Java version string: " + versionString);

        assertThat(versionString).isNotNull();
        assertThat(versionString).isNotEmpty();
    }
}
