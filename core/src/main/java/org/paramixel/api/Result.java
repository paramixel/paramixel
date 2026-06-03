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

import java.time.Instant;
import java.util.Optional;
import org.paramixel.api.action.Action;

/**
 * Represents the outcome of a Paramixel run, providing access to the root descriptor
 * and effective aggregate state derived from the descriptor tree and configuration.
 *
 * <p>The effective outcome is the most-severe outcome found across the descriptor tree,
 * with configuration-based promotion rules applied:
 * <ul>
 *   <li>skipped is promoted to failed when {@link Configuration#FAILURE_ON_SKIP} is {@code true}</li>
 *   <li>aborted is promoted to failed when {@link Configuration#FAILURE_ON_ABORT} is {@code true} (the default)</li>
 *   <li>when no descriptor was discovered, the effective outcome is failed when
 *       {@link Configuration#FAIL_IF_NO_TESTS} is {@code true}, otherwise skipped</li>
 * </ul>
 *
 * <p>Severity ordering for tree aggregation: failed &gt; aborted &gt; pending/running &gt; skipped &gt; passed.
 *
 * @see Runner#run(Action)
 */
public interface Result {

    /**
     * Returns whether the effective run outcome completed successfully.
     *
     * @return {@code true} when the effective outcome is passed
     */
    boolean isPassed();

    /**
     * Returns whether the effective run outcome is failed.
     *
     * @return {@code true} when the effective outcome is failed
     */
    boolean isFailed();

    /**
     * Returns whether the effective run outcome is skipped.
     *
     * @return {@code true} when the effective outcome is skipped
     */
    boolean isSkipped();

    /**
     * Returns whether the effective run outcome is aborted.
     *
     * @return {@code true} when the effective outcome is aborted
     */
    boolean isAborted();

    /**
     * Returns the earliest start instant found in the descriptor tree.
     *
     * @return the earliest descriptor start instant, or empty when no descriptor has started
     */
    Optional<Instant> startedAt();

    /**
     * Returns the latest completion instant found in the descriptor tree.
     *
     * @return the latest descriptor completion instant, or empty when no descriptor has completed
     */
    Optional<Instant> completedAt();

    /**
     * Returns the root descriptor of the executed action tree.
     *
     * <p>When no action was discovered or execution failed before descriptor creation,
     * the returned optional is empty.
     *
     * @return the root descriptor, or empty when no descriptor was discovered
     */
    Optional<Descriptor> descriptor();
}
