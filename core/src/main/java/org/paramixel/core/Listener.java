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

/**
 * Receives lifecycle callbacks during action execution.
 *
 * <p>A {@link Listener} can observe the overall run as well as individual action execution events. All methods provide
 * default no-op implementations so callers may override only the callbacks they need.
 *
 * <p>Listeners that hold resources (such as open file handles) should override {@link #close()} to release them.
 * Callers that supply a custom listener to {@link Runner.Builder#listener(Listener)} are responsible for ensuring the
 * listener's resources are released — either by wrapping the runner in try-with-resources or by closing the listener
 * directly.
 *
 * @implSpec Listener implementations should avoid throwing exceptions. The default runner wraps listeners in a safe
 *     adapter when needed so non-{@link Error} throwables are reported instead of aborting the run.
 */
public interface Listener extends AutoCloseable {

    /**
     * Invoked once before the runner starts executing the requested action tree.
     *
     * @param runner the active runner
     */
    default void runStarted(Runner runner) {
        // Intentionally empty
    }

    /**
     * Invoked immediately before an action begins execution.
     *
     * @param result the mutable or in-progress result associated with the action about to execute
     */
    default void beforeAction(Result result) {
        // Intentionally empty
    }

    /**
     * Invoked when action execution throws an exception or error condition that is being reported.
     *
     * @param result the result associated with the failing action
     * @param throwable the reported throwable
     */
    default void actionThrowable(Result result, Throwable throwable) {
        // Intentionally empty
    }

    /**
     * Invoked after an action finishes execution.
     *
     * @param result the completed result for the action
     */
    default void afterAction(Result result) {
        // Intentionally empty
    }

    /**
     * Invoked when an action is skipped rather than executed.
     *
     * @param result the result representing the skipped action
     */
    default void skipAction(Result result) {
        // Intentionally empty
    }

    /**
     * Invoked once after the run completes.
     *
     * @param runner the active runner
     * @param result the root result for the completed run
     */
    default void runCompleted(Runner runner, Result result) {
        // Intentionally empty
    }

    /**
     * Releases resources held by this listener.
     *
     * <p>The default implementation does nothing. Listeners that hold resources should override this method to
     * release them.
     */
    @Override
    default void close() {
        // Intentionally empty
    }
}
