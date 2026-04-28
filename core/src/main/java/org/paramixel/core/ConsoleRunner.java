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

import java.util.Optional;

/**
 * Executes resolved actions using the standard console runtime.
 *
 * <p>This utility resolves an {@link Action} from a supplied {@link Selector},
 * executes it with the default {@link Runner}, and provides convenience
 * methods for converting the result into a process exit code.</p>
 *
 * <p>If no action is resolved for the selector, execution is skipped and
 * {@link Optional#empty()} is returned from {@link #run(Selector)}.</p>
 *
 * <p>This class cannot be instantiated.</p>
 */
public final class ConsoleRunner {

    /**
     * Prevents instantiation of this utility class.
     */
    private ConsoleRunner() {}

    /**
     * Resolves and executes an action using the default runner.
     *
     * @param selector selector used to resolve an action
     * @return the execution result, or {@link Optional#empty()} if no action was resolved
     */
    public static Optional<Result> run(Selector selector) {
        Optional<Action> optionalAction = Resolver.resolveActions(selector);

        if (optionalAction.isEmpty()) {
            return Optional.empty();
        }

        Runner runner = Runner.builder().build();
        return Optional.of(runner.run(optionalAction.orElseThrow()));
    }

    /**
     * Executes an action and converts the result to a process exit code.
     *
     * <ul>
     *     <li>{@code 0} if no action was resolved</li>
     *     <li>{@code 0} if execution passed</li>
     *     <li>{@code 1} if execution failed</li>
     * </ul>
     *
     * @param selector selector used to resolve an action
     * @return process exit code representing the execution result
     */
    public static int runAndReturnExitCode(Selector selector) {
        Optional<Result> result = run(selector);

        if (result.isEmpty()) {
            return 0;
        }

        return result.get().status() == Result.Status.PASS ? 0 : 1;
    }

    /**
     * Executes an action and terminates the JVM using the resulting exit code.
     *
     * @param selector selector used to resolve an action
     */
    public static void runAndExit(Selector selector) {
        System.exit(runAndReturnExitCode(selector));
    }
}
