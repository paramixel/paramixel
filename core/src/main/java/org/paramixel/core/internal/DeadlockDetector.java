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

package org.paramixel.core.internal;

import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.CompositeAction;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.exception.DeadlockDetected;
import org.paramixel.core.support.Arguments;

/**
 * Detects nested default-executor {@link Parallel} configurations that can starve the shared pool.
 *
 * <p>This validator counts consecutive levels of {@link Parallel} actions that all rely on the runner-managed shared
 * executor. Dedicated executor services break the chain because they do not compete for the same threads.
 */
public final class DeadlockDetector {

    /**
     * Creates a new deadlock detector.
     */
    public DeadlockDetector() {}

    /**
     * Validates that the supplied action tree does not exceed the available shared parallelism depth.
     *
     * @param root the root action to validate
     * @param parallelism the size of the shared parallel executor pool
     * @throws NullPointerException if {@code root} is {@code null}
     * @throws DeadlockDetected if the action tree can deadlock due to thread starvation
     */
    public void validateNoDeadlock(Action root, int parallelism) {
        Objects.requireNonNull(root, "root must not be null");
        Arguments.requirePositive(parallelism, "parallelism must be positive, was: " + parallelism);

        int maxDepth = maxParallelDepth(root, 0);
        if (maxDepth > parallelism + 1) {
            throw DeadlockDetected.of(
                    "Potential thread-starvation deadlock detected: the action tree contains " + maxDepth
                            + " levels of nested default-executor Parallel actions,"
                            + " but the shared parallel executor pool has only " + parallelism + " thread(s)."
                            + " Supply a dedicated ExecutorService to inner Parallel actions"
                            + " via Parallel.builder(name).executorService(executorService).child(child).build()"
                            + " or increase paramixel.parallelism to at least " + (maxDepth - 1) + ".");
        }
    }

    private int maxParallelDepth(Action action, int currentDepth) {
        if (action instanceof Parallel p && p.getExecutorService().isEmpty()) {
            currentDepth++;
        } else if (action instanceof Parallel p && p.getExecutorService().isPresent()) {
            currentDepth = 0;
        }
        int max = currentDepth;
        if (action instanceof CompositeAction compositeAction) {
            for (Action child : compositeAction.getChildren()) {
                max = Math.max(max, maxParallelDepth(child, currentDepth));
            }
        }
        return max;
    }
}
