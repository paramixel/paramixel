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
 * Executes multiple cleanup tasks and records any exceptions.
 *
 * <p>This executor attempts each registered cleanup action, even if earlier actions fail, and then
 * allows callers to inspect or throw the collected failures.
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
     * Adds a cleanup task.
     *
     * @param throwableTask the throwable task to add
     * @return this executor for chaining
     */
    public CleanupExecutor addTask(final @NonNull ThrowableTask throwableTask) {
        throwableTasks.add(throwableTask);

        return this;
    }

    /**
     * Adds a cleanup task that runs only when the supplied value is present (non-null).
     *
     * @param supplier      the supplier of the value
     * @param cleanupAction cleanup action to run with the supplied value
     * @param <T>           the type of the value
     * @return this executor for chaining
     */
    public <T> CleanupExecutor addTaskIfPresent(
            final @NonNull Supplier<? extends T> supplier, final @NonNull Consumer<? super T> cleanupAction) {
        return addTask(() -> Optional.ofNullable(supplier.get()).ifPresent(cleanupAction));
    }

    /**
     * Adds multiple cleanup tasks.
     *
     * @param cleanupActions the throwable tasks to add
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
     * @return an unmodifiable view of the registered tasks
     */
    public List<ThrowableTask> cleanupActions() {
        return Collections.unmodifiableList(throwableTasks);
    }

    /**
     * Returns the exceptions recorded during {@link #execute()}.
     *
     * <p>The returned list has one entry per registered task. If a task completed successfully, the
     * corresponding entry is {@code null}.
     *
     * @return an unmodifiable view of recorded exceptions
     */
    public List<Exception> throwables() {
        return Collections.unmodifiableList(exceptions);
    }

    /**
     * Runs all registered cleanup tasks in order.
     *
     * <p>This method may be invoked at most once.
     *
     * @return this executor for chaining
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
     * Indicates whether any task failed.
     *
     * @return {@code true} if at least one recorded exception is non-null
     */
    public boolean hasThrowables() {
        for (Exception exception : exceptions) {
            if (exception != null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Throws the first recorded exception, suppressing any additional failures.
     *
     * @throws Exception the first recorded exception
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
