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

package examples.testcontainers.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

/**
 * Utility class to execute multiple cleanup tasks, collecting any thrown throwables.
 * This is useful for scenarios where multiple cleanup actions need to be performed, and we want to
 * ensure that all actions are attempted even if some of them fail, while still being able to report
 * any failures that occur during the cleanup process.
 */
public final class CleanupExecutor {

    private final List<ThrowableTask> throwableTasks = new ArrayList<>();
    private final List<Exception> exceptions = new ArrayList<>();
    private boolean hasRun = false;

    /**
     * Constructs a CleanupExecutor.
     */
    public CleanupExecutor() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Adds a throwable task
     *
     * @param throwableTask the throwable task to add
     * @return this CleanupExecutor
     */
    public CleanupExecutor addTask(final @NonNull ThrowableTask throwableTask) {
        throwableTasks.add(throwableTask);

        return this;
    }

    /**
     * Adds a throwable task that is executed only if the supplied value is present (non-null)
     *
     * @param supplier      the supplier of the value
     * @param cleanupAction the throwable task to add
     * @param <T>           the type of the value
     * @return this CleanupExecutor
     */
    public <T> CleanupExecutor addTaskIfPresent(
            final @NonNull Supplier<? extends T> supplier, final @NonNull Consumer<? super T> cleanupAction) {
        return addTask(() -> Optional.ofNullable(supplier.get()).ifPresent(cleanupAction));
    }

    /**
     * Adds multiple throwable tasks
     *
     * @param cleanupActions the throwable tasks to add
     * @return this CleanupExecutor
     */
    public CleanupExecutor addTasks(final @NonNull List<? extends ThrowableTask> cleanupActions) {
        for (ThrowableTask throwableTask : cleanupActions) {
            addTask(throwableTask);
        }

        return this;
    }

    /**
     * Gets the list of added throwable tasks
     *
     * @return the list of added throwable tasks
     */
    public List<ThrowableTask> cleanupActions() {
        return Collections.unmodifiableList(throwableTasks);
    }

    /**
     * Gets the list of failures occurred during execution. A failure is represented by a Throwable.
     * For any throwable task that throws a Throwable during execution,
     * the Throwable is collected in this list, else null is added.
     *
     * @return the list of failures
     */
    public List<Throwable> throwables() {
        return Collections.unmodifiableList(exceptions);
    }

    /**
     * Runs all added throwable tasks in order, collecting any thrown throwables.
     *
     * @return this CleanupExecutor
     */
    public CleanupExecutor execute() {
        if (hasRun) {
            throw new IllegalStateException("CleanupExecutor has already run");
        }

        hasRun = true;
        exceptions.clear();

        for (ThrowableTask throwableTask : throwableTasks) {
            try {
                throwableTask.run();
                exceptions.add(null);
            } catch (Exception e) {
                exceptions.add(e);
            }
        }

        return this;
    }

    /**
     * Checks if there are any throwables collected during execution
     *
     * @return true if there are throwables, false otherwise
     */
    public boolean hasThrowables() {
        return !exceptions.isEmpty();
    }

    /**
     * Throws the first throwable in the list of failures, if any.
     *
     * @throws Exception the first exception in the list of failures
     */
    public void throwIfFailed() throws Exception {
        if (!hasRun) {
            execute();
        }

        Exception firstException = null;

        for (Exception exception : exceptions) {
            if (exception == null) {
                continue;
            }

            if (firstException == null) {
                firstException = exception;
            } else {
                firstException.addSuppressed(exception);
            }
        }

        if (firstException != null) {
            throw firstException;
        }
    }
}
