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

package examples.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CleanupRunner {

    private final List<ThrowingRunnable> tasks = new ArrayList<>();

    private final List<Throwable> exceptions = new ArrayList<>();

    private boolean hasExecuted = false;

    public CleanupRunner() {}

    public int taskCount() {
        return tasks.size();
    }

    public CleanupRunner addTask(final ThrowingRunnable task) {
        Objects.requireNonNull(task, "task must not be null");
        tasks.add(task);
        return this;
    }

    public <T> CleanupRunner addTask(final Optional<T> value, final Consumer<? super T> action) {
        Objects.requireNonNull(action, "action must not be null");
        return addTask(() -> value.ifPresent(action));
    }

    public CleanupRunner addTasks(final List<? extends ThrowingRunnable> tasksToAdd) {
        Objects.requireNonNull(tasksToAdd, "tasksToAdd must not be null");
        for (ThrowingRunnable task : tasksToAdd) {
            addTask(task);
        }
        return this;
    }

    public CleanupRunner addTaskWhen(final Supplier<Boolean> condition, final Runnable action) {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(action, "action must not be null");
        return addTask(() -> {
            if (condition.get()) {
                action.run();
            }
        });
    }

    public CleanupRunner addTaskWhen(final boolean condition, final Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        return addTask(() -> {
            if (condition) {
                action.run();
            }
        });
    }

    public List<ThrowingRunnable> tasks() {
        return Collections.unmodifiableList(tasks);
    }

    public CleanupRunner execute() {
        if (hasExecuted) {
            throw new IllegalStateException("CleanupRunner has already executed");
        }

        hasExecuted = true;
        exceptions.clear();

        for (int i = tasks.size() - 1; i >= 0; i--) {
            try {
                tasks.get(i).run();
                exceptions.add(null);
            } catch (Throwable e) {
                exceptions.add(e);
            }
        }

        return this;
    }

    public boolean hasExceptions() {
        for (Throwable exception : exceptions) {
            if (exception != null) {
                return true;
            }
        }
        return false;
    }

    public Optional<Throwable> getException(final int index) {
        if (!hasExecuted || index < 0 || index >= exceptions.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(exceptions.get(index));
    }

    public boolean isSuccess(final int index) {
        if (!hasExecuted || index < 0 || index >= exceptions.size()) {
            return false;
        }
        return exceptions.get(index) == null;
    }

    public Map<Integer, Throwable> exceptionsByIndex() {
        if (!hasExecuted) {
            return Map.of();
        }
        var failures = new HashMap<Integer, Throwable>();
        for (int i = 0; i < exceptions.size(); i++) {
            Throwable exception = exceptions.get(i);
            if (exception != null) {
                failures.put(i, exception);
            }
        }
        return Collections.unmodifiableMap(failures);
    }

    public void executeAndThrow() throws Throwable {
        if (!hasExecuted) {
            execute();
        }

        Throwable firstException = null;

        for (Throwable exception : exceptions) {
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
