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

import java.util.Optional;
import nonapi.org.paramixel.listener.DefaultListeners;
import nonapi.org.paramixel.listener.SafeListener;

/**
 * Receives run, discovery, and descriptor execution callbacks.
 *
 * <p>Actions own descriptor execution callbacks and invoke {@link #onBeforeExecution(Descriptor)}
 * and {@link #onAfterExecution(Descriptor)} around their own execution boundary. The runner invokes
 * run-level and discovery callbacks.
 */
public interface Listener {

    /**
     * Returns the standard listener chain using the supplied configuration.
     *
     * <p>The chain contains the status and summary listeners, plus a report listener when
     * {@link Configuration#REPORT_FILE} is set. Prefer {@code Runner.builder().build()}, which wires this chain
     * for you.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @return the default listener chain; never {@code null}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    static Listener defaultListener(final Configuration configuration) {
        return DefaultListeners.create(configuration);
    }

    /**
     * Invoked exactly once when a run starts, before any warning, discovery or execution callback.
     *
     * <p>This is the first callback of a run: it replaces the former separate {@code initialize} callback, so a
     * listener configures itself here.
     *
     * @param configuration the effective configuration of the run; never {@code null}
     */
    default void onRunStarted(final Configuration configuration) {
        // Intentionally empty
    }

    /**
     * Invoked once before discovery begins, after {@link #onRunStarted(Configuration)}.
     *
     * <p>Implementations are responsible for thread-safety if they maintain mutable state.
     */
    default void onDiscoveryStarted() {
        // Intentionally empty
    }

    /**
     * Invoked after discovery creates the descriptor tree, or when discovery finds nothing.
     *
     * @param root the discovered root descriptor, or empty when no descriptor was discovered
     */
    default void onDiscoveryCompleted(final Optional<Descriptor> root) {
        // Intentionally empty
    }

    /**
     * Invoked when the runner detects a condition that does not fail the run but affects behavior.
     *
     * <p>Warnings report conditions that would otherwise be silently ignored: a {@code paramixel.match.*} key that
     * the selector passed to {@code Runner.run(Selector)} does not use, and an unknown {@code paramixel.*} key.
     * Warnings are delivered once per condition, after {@link #onRunStarted(Configuration)} and before execution.
     *
     * <p>Implementations must not throw; {@link SafeListener} isolates recoverable failures.
     *
     * @param message the human-readable warning; never {@code null}
     */
    default void onWarning(final String message) {
        // Intentionally empty
    }

    /**
     * Invoked immediately before a descriptor's execution logic runs.
     *
     * <p>This callback and {@link #onAfterExecution(Descriptor)} are invoked <strong>concurrently</strong> from
     * pool worker threads, because a {@link org.paramixel.api.action.Parallel} runs its children at the same time.
     * Implementations must be thread-safe; {@link SafeListener} isolates recoverable failures.
     *
     * @param descriptor the descriptor about to execute; never {@code null}
     */
    default void onBeforeExecution(final Descriptor descriptor) {
        // Intentionally empty
    }

    /**
     * Invoked after a descriptor reaches a terminal status.
     *
     * <p>Invoked concurrently, exactly as {@link #onBeforeExecution(Descriptor)}.
     *
     * @param descriptor the completed descriptor; never {@code null}
     */
    default void onAfterExecution(final Descriptor descriptor) {
        // Intentionally empty
    }

    /**
     * Invoked once after the run completes.
     *
     * @param result the run result containing the root descriptor and effective aggregate status; never {@code null}
     */
    default void onRunCompleted(final Result result) {
        // Intentionally empty
    }
}
