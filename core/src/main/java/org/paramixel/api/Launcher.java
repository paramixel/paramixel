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

/**
 * Command-line entry point for a discovered Paramixel run.
 *
 * <p>Discovers and executes every {@code @Paramixel.Factory} on the classpath, then exits the JVM with
 * {@link Result#exitCode()}. Use it as a {@code main} class, for example from a Gradle {@code JavaExec} task or a
 * Maven plugin configuration.
 *
 * @see Runner#run()
 */
public final class Launcher {

    private Launcher() {
        // Intentionally empty
    }

    /**
     * Runs discovery and exits the JVM with the run's exit code.
     *
     * @param args command-line arguments; ignored
     */
    public static void main(final String[] args) {
        System.exit(Runner.builder().build().run().exitCode());
    }
}
