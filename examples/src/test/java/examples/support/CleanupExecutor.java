/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

package examples.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

/**
 * Executes cleanup tasks and aggregates any thrown exceptions.
 */
public final class CleanupExecutor {

    /**
     * Registered cleanup tasks to execute.
     */
    private final List<ThrowableTask> throwableTasks = new ArrayList<>();

    /**
     * Collected throwables from task execution.
     */
    private final List<Throwable> throwables = new ArrayList<>();

    /**
     * Tracks whether execution has already occurred.
     */
    private boolean hasRun = false;

    /**
     * Creates a new cleanup executor.
     */
    public CleanupExecutor() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Adds a cleanup task to execute.
     *
     * @param throwableTask the cleanup task to add
     * @return this executor for chaining
     */
    public CleanupExecutor addTask(final @NonNull ThrowableTask throwableTask) {
        throwableTasks.add(throwableTask);

        return this;
    }

    /**
     * Adds a cleanup task that runs only when the supplier returns a value.
     *
     * @param supplier the supplier of the value
     * @param cleanupAction the cleanup action to run
     * @param <T> the value type
     * @return this executor for chaining
     */
    public <T> CleanupExecutor addTaskIfPresent(
            final @NonNull Supplier<? extends T> supplier, final @NonNull Consumer<? super T> cleanupAction) {
        return addTask(() -> Optional.ofNullable(supplier.get()).ifPresent(cleanupAction));
    }

    /**
     * Adds multiple cleanup tasks.
     *
     * @param cleanupActions the cleanup tasks to add
     * @return this executor for chaining
     */
    public CleanupExecutor addTasks(final @NonNull List<? extends ThrowableTask> cleanupActions) {
        for (ThrowableTask throwableTask : cleanupActions) {
            addTask(throwableTask);
        }

        return this;
    }

    /**
     * Returns the registered cleanup tasks.
     *
     * @return an unmodifiable list of cleanup tasks
     */
    public List<ThrowableTask> cleanupActions() {
        return Collections.unmodifiableList(throwableTasks);
    }

    /**
     * Returns the collected throwables from execution.
     *
     * @return an unmodifiable list of throwables (entries may be {@code null})
     */
    public List<Throwable> throwables() {
        return Collections.unmodifiableList(throwables);
    }

    /**
     * Executes all registered cleanup tasks in order.
     *
     * @return this executor for chaining
     */
    public CleanupExecutor execute() {
        if (hasRun) {
            throw new IllegalStateException("CleanupExecutor has already run");
        }

        hasRun = true;
        throwables.clear();

        for (ThrowableTask throwableTask : throwableTasks) {
            try {
                throwableTask.run();
                throwables.add(null);
            } catch (Throwable t) {
                throwables.add(t);
            }
        }

        return this;
    }

    /**
     * Indicates whether any throwables were collected.
     *
     * @return {@code true} if any throwables were recorded
     */
    public boolean hasThrowables() {
        return !throwables.isEmpty();
    }

    /**
     * Throws the first collected throwable, if present.
     *
     * @throws Throwable the first failure, with additional failures suppressed
     */
    public void throwIfFailed() throws Throwable {
        if (!hasRun) {
            execute();
        }

        Throwable firstThrowable = null;

        for (Throwable throwable : throwables) {
            if (throwable == null) {
                continue;
            }

            if (firstThrowable == null) {
                firstThrowable = throwable;
            } else {
                firstThrowable.addSuppressed(throwable);
            }
        }

        if (firstThrowable != null) {
            throw firstThrowable;
        }
    }
}
