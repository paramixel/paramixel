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
 * The outcome of a Paramixel run: the root descriptor tree plus the effective status and exit code.
 *
 * <p>The effective status is the <em>root</em> descriptor's already-aggregated status, with these
 * configuration-based promotions applied:
 * <ul>
 *   <li>{@code SKIPPED} becomes {@code FAILED} when {@link Configuration#FAILURE_ON_SKIP} is {@code true}</li>
 *   <li>{@code ABORTED} becomes {@code FAILED} when {@link Configuration#FAILURE_ON_ABORT} is {@code true}
 *       (the default)</li>
 *   <li>when no descriptor was discovered, the effective status is {@code FAILED} when
 *       {@link Configuration#FAIL_IF_NO_TESTS} is {@code true}, otherwise {@code SKIPPED}</li>
 * </ul>
 * A promotion keeps the original message and throwable.
 *
 * <p>This is deliberately not "the most severe status anywhere in the tree": a {@link
 * org.paramixel.api.action.Loop} that retried and then passed has {@code FAILED} iterations beneath a
 * {@code PASSED} root, and the run passed.
 *
 * @see Runner#run(Action)
 */
public interface Result {

    /**
     * Returns the effective status of this run.
     *
     * @return the effective status; never {@code null}
     */
    Status status();

    /**
     * Returns the process exit code for this run.
     *
     * <p>This is the single authority for exit codes: {@code 0} when the effective status is
     * {@code PASSED}, {@code SKIPPED} or {@code ABORTED}, and {@code 1} when it is {@code FAILED}.
     * Every configuration promotion is already reflected in {@link #status()}.
     *
     * @return the exit code
     */
    int exitCode();

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
