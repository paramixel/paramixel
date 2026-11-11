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
 * <p>The launcher owns {@link System#exit(int)}; {@link Runner} never terminates the JVM. Exit codes:
 *
 * <ul>
 *   <li>{@code 0} — the run passed under the configured exit policy, or help was requested.</li>
 *   <li>{@code 1} — the run failed, was canceled, or was mapped to failure by configuration.</li>
 *   <li>{@code 2} — a usage error or an invalid configuration, definition, or discovery result.</li>
 * </ul>
 *
 * <p>Supported arguments: {@code --help} prints usage; any other argument is a usage error. Use
 * {@link Runner#run(org.paramixel.api.selector.Selector)} or {@code paramixel.match.*} configuration to select a
 * subset of discovered factories.
 */
public final class Launcher {

    private static final int EXIT_USAGE = 2;

    private Launcher() {
        // Intentionally empty
    }

    /**
     * Runs discovery and exits the JVM with the resulting exit code.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        System.exit(execute(args));
    }

    /**
     * Executes the launcher without terminating the JVM.
     *
     * @param args command-line arguments
     * @return the process exit code
     */
    public static int execute(final String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    printUsage();
                    return 0;
                }
                if (arg != null && !arg.isBlank()) {
                    System.err.println("Unknown argument: " + arg);
                    printUsage();
                    return EXIT_USAGE;
                }
            }
        }
        try (var runner = Runner.builder().build()) {
            return runner.run().exitCode();
        } catch (RuntimeException e) {
            System.err.println("Paramixel run failed to start: " + e.getMessage());
            return EXIT_USAGE;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: paramixel [--help]");
        System.out.println();
        System.out.println("Discovers and executes every @Paramixel.Factory on the classpath.");
        System.out.println("Configuration keys are read from paramixel.properties and JVM system properties.");
    }
}
