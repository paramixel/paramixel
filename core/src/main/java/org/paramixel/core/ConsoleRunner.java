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
import org.paramixel.core.discovery.Resolver;
import org.paramixel.core.discovery.Selector;

/**
 * Executes actions using the standard console-oriented runtime.
 *
 * <p>ConsoleRunner provides convenience methods for resolving actions from a {@link Selector},
 * running actions directly, converting outcomes to process exit codes, and terminating the JVM.</p>
 *
 * @see Selector
 * @see Runner
 * @see ConsoleRunner#run(Selector)
 * @see ConsoleRunner#runAndExit(Selector)
 */
public final class ConsoleRunner {

    private ConsoleRunner() {}

    /**
     * Resolves an action from the supplied selector and executes it.
     *
     * @param selector the selector used to resolve an action
     * @return an optional containing the execution result, or an empty optional when no action is resolved
     */
    public static Optional<Result> run(Selector selector) {
        Optional<Action> optionalAction = Resolver.resolveActions(selector);
        if (optionalAction.isEmpty()) {
            return Optional.empty();
        }
        Action action = optionalAction.get();
        Runner.builder().build().run(action);
        return Optional.of(action.getResult());
    }

    /**
     * Resolves an action from the supplied selector, executes it, and returns a process exit code.
     *
     * <p>Returns {@code 0} when no action is resolved or when the resolved action passes. Returns
     * {@code 1} for all other resolved outcomes.</p>
     *
     * @param selector the selector used to resolve an action
     * @return the process exit code for the resolved execution result
     */
    public static int runAndReturnExitCode(Selector selector) {
        Optional<Result> result = run(selector);
        return result.filter(value -> !value.getStatus().isPass())
                .map(value -> 1)
                .orElse(0);
    }

    /**
     * Resolves an action from the supplied selector, executes it, and exits the JVM with the
     * corresponding process exit code.
     *
     * @param selector the selector used to resolve an action
     */
    public static void runAndExit(Selector selector) {
        System.exit(runAndReturnExitCode(selector));
    }

    /**
     * Executes the supplied action.
     *
     * @param action the action to execute
     * @return the execution result
     */
    public static Result run(Action action) {
        Runner.builder().build().run(action);
        return action.getResult();
    }

    /**
     * Executes the supplied action and returns a process exit code.
     *
     * <p>Returns {@code 0} when the action passes or is skipped. Returns {@code 1} for all other
     * outcomes.</p>
     *
     * @param action the action to execute
     * @return the process exit code for the action result
     */
    public static int runAndReturnExitCode(Action action) {
        Result result = run(action);
        return result.getStatus().isPass() || result.getStatus().isSkip() ? 0 : 1;
    }

    /**
     * Executes the supplied action and exits the JVM with the corresponding process exit code.
     *
     * @param action the action to execute
     */
    public static void runAndExit(Action action) {
        System.exit(runAndReturnExitCode(action));
    }
}
