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

import org.paramixel.core.listener.Listeners;

/**
 * Receives notifications around action execution.
 */
public interface Listener {

    /**
     * Creates the default execution listener.
     *
     * @return A default listener instance.
     */
    static Listener defaultListener() {
        return Listeners.defaultListener();
    }

    /**
     * Creates a listener with tree-formatted summary output.
     *
     * @return A listener with tree summary.
     */
    static Listener treeListener() {
        return Listeners.treeListener();
    }

    /**
     * Invoked before the execution plan starts.
     *
     * @param runner The runner executing the plan; must not be null.
     * @param action The root action to be executed; must not be null.
     */
    default void planStarted(Runner runner, Action action) {}

    /**
     * Invoked after the execution plan completes.
     *
     * @param runner The runner that executed the plan; must not be null.
     * @param result The root execution result; must not be null.
     */
    default void planCompleted(Runner runner, Result result) {}

    /**
     * Invoked before an action starts.
     *
     * @param context The active context; must not be null.
     * @param action The action about to execute; must not be null.
     */
    default void beforeAction(Context context, Action action) {}

    /**
     * Invoked after an action finishes.
     *
     * @param context The active context; must not be null.
     * @param action The completed action; must not be null.
     * @param result The execution result; must not be null.
     */
    default void afterAction(Context context, Action action, Result result) {}
}
