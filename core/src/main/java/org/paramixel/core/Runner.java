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

import java.util.Map;
import org.paramixel.core.internal.DefaultRunner;

/**
 * Coordinates the execution of actions and action groups.
 */
public interface Runner {

    /**
     * Returns the listener notified during execution.
     *
     * @return The execution listener.
     */
    Listener listener();

    /**
     * Returns the configuration map for this runner.
     *
     * @return The configuration map; never null.
     */
    default Map<String, String> configuration() {
        return Configuration.defaultProperties();
    }

    /**
     * Executes an action as a root action.
     *
     * @param action The action to execute; must not be null.
     * @return The execution result.
     */
    Result run(Action action);

    /**
     * Executes the action represented by an existing context.
     *
     * @param context The context to execute; must not be null.
     * @return The execution result.
     */
    Result run(Context context);

    /**
     * Executes an action as a child of an existing context.
     *
     * @param action The action to execute; must not be null.
     * @param parentContext The parent context; must not be null.
     * @return The execution result.
     */
    Result run(Action action, Context parentContext);

    static Builder builder() {
        return new Builder();
    }

    /**
     * Builds {@link Runner} instances with optional custom components.
     */
    class Builder {

        private Runner runner;
        private final DefaultRunner.Builder defaultRunnerBuilder = DefaultRunner.builder();
        private Map<String, String> configuration;

        /**
         * Uses an existing runner instead of building a default runner.
         *
         * @param runner The runner to return from {@link #build()}; may be
         *     null to use the default builder.
         * @return This builder.
         */
        public Builder runner(Runner runner) {
            this.runner = runner;
            return this;
        }

        /**
         * Sets the listener used by the default runner.
         *
         * @param listener The listener to notify; must not be null.
         * @return This builder.
         */
        public Builder listener(Listener listener) {
            defaultRunnerBuilder.listener(listener);
            return this;
        }

        /**
         * Sets the configuration for the default runner.
         *
         * @param properties The configuration properties; must not be null.
         * @return This builder.
         */
        public Builder configuration(Map<String, String> properties) {
            defaultRunnerBuilder.configuration(properties);
            return this;
        }

        /**
         * Builds the configured runner.
         *
         * @return The configured custom runner, or a default runner.
         */
        public Runner build() {
            if (runner != null) {
                return runner;
            }
            return defaultRunnerBuilder.build();
        }
    }
}
