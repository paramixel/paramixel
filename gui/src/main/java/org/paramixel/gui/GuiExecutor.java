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

package org.paramixel.gui;

import java.util.Optional;
import org.paramixel.core.Action;
import org.paramixel.core.Resolver;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Selector;

/**
 * Executes resolved actions using a graphical user interface listener.
 *
 * <p>This utility resolves an {@link Action} from a supplied {@link Selector},
 * runs the action through the standard {@link Runner}, and attaches a
 * {@link GuiExecutionListener} to display execution progress in a GUI.</p>
 *
 * <p>If no action is resolved for the selector, execution is skipped and
 * {@link Optional#empty()} is returned from {@link #run(Selector)}.</p>
 *
 * <p>This class cannot be instantiated.</p>
 */
public final class GuiExecutor {

    /**
     * Prevents instantiation of this utility class.
     */
    private GuiExecutor() {}

    /**
     * Resolves and executes an action using a GUI listener.
     *
     * <p>If an action is found for the provided selector, it is executed using
     * the standard executor with a {@link GuiExecutionListener}. This method
     * blocks until the GUI listener thread completes.</p>
     *
     * @param selector selector used to resolve an action
     * @return the execution result, or {@link Optional#empty()} if no action was resolved
     * @throws InterruptedException if interrupted while waiting for the GUI
     *                              listener to complete
     */
    public static Optional<Result> run(Selector selector) throws InterruptedException {
        Optional<Action> optionalAction = Resolver.resolveActions(selector);

        if (optionalAction.isEmpty()) {
            return Optional.empty();
        }

        Action action = optionalAction.get();
        GuiExecutionListener guiExecutionListener = new GuiExecutionListener(action);

        Runner runner = Runner.builder().listener(guiExecutionListener).build();
        Result result = runner.run(action);

        guiExecutionListener.join();

        return Optional.of(result);
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
     * @throws InterruptedException if interrupted while waiting for execution
     *                              to complete
     */
    public static int runAndReturnExitCode(Selector selector) throws InterruptedException {
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
     * @throws InterruptedException if interrupted while waiting for execution
     *                              to complete
     */
    public static void runAndExit(Selector selector) throws InterruptedException {
        System.exit(runAndReturnExitCode(selector));
    }
}
