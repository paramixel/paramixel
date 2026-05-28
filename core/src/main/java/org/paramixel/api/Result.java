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
import org.paramixel.api.action.Spec;

/**
 * Represents the outcome of a Paramixel run, providing access to the root descriptor
 * and the effective aggregate status derived from the descriptor tree and configuration.
 *
 * <p>The effective status is the most-severe status found across the entire descriptor tree,
 * with configuration-based promotion rules applied:
 * <ul>
 *   <li>{@link Status#SKIPPED} is promoted to {@link Status#FAILED} when
 *       {@link Configuration#FAILURE_ON_SKIP} is {@code true}</li>
 *   <li>{@link Status#ABORTED} is promoted to {@link Status#FAILED} when
 *       {@link Configuration#FAILURE_ON_ABORT} is {@code true} (the default)</li>
 *   <li>When no descriptor was discovered, the effective status is {@link Status#FAILED}
 *       when {@link Configuration#FAIL_IF_NO_TESTS} is {@code true},
 *       otherwise {@link Status#SKIPPED}</li>
 * </ul>
 *
 * <p>Severity ordering for tree aggregation: {@code FAILED} > {@code ABORTED} >
 * {@code PENDING}/{@code RUNNING} > {@code SKIPPED} > {@code PASSED}.
 *
 * @see Runner#run(Spec)
 * @see Status
 */
public interface Result {

    /**
     * Returns the root descriptor of the executed action tree.
     *
     * <p>When no action was discovered or execution failed before descriptor creation,
     * the returned optional is empty.
     *
     * @return the root descriptor, or empty when no descriptor was discovered
     */
    Optional<Descriptor> descriptor();

    /**
     * Returns the effective aggregate status derived from the descriptor tree and configuration.
     *
     * <p>The aggregate status is the most-severe status found by walking the entire descriptor tree.
     * Configuration promotion rules are then applied: {@link Status#SKIPPED} may be promoted to
     * {@link Status#FAILED} when {@link Configuration#FAILURE_ON_SKIP} is {@code true},
     * and {@link Status#ABORTED} may be promoted to {@link Status#FAILED} when
     * {@link Configuration#FAILURE_ON_ABORT} is {@code true}.
     *
     * <p>When no descriptor was discovered, returns {@link Status#FAILED} when
     * {@link Configuration#FAIL_IF_NO_TESTS} is {@code true}, otherwise {@link Status#SKIPPED}.
     *
     * @return the effective aggregate status; never {@code null}
     */
    Status status();
}
