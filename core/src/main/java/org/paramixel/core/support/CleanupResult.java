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

package org.paramixel.core.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes the outcome of executing all cleanup tasks registered with a {@link Cleanup}.
 *
 * <p>Instances are immutable and obtained exclusively via {@link Cleanup#run()}.
 * Each result captures the number of tasks executed and any exceptions thrown,
 * indexed by the original registration order.
 */
public final class CleanupResult {

    private final int executableCount;

    private final List<Throwable> exceptions;

    CleanupResult(int executableCount, List<Throwable> exceptions) {
        this.executableCount = executableCount;
        this.exceptions =
                Collections.unmodifiableList(Objects.requireNonNull(exceptions, "exceptions must not be null"));
    }

    /**
     * Returns the number of cleanup tasks that were executed.
     *
     * @return The number of executed cleanup tasks.
     */
    public int getExecutableCount() {
        return executableCount;
    }

    /**
     * Returns whether any executable threw an exception during execution.
     *
     * @return {@code true} if any executable threw an exception during execution.
     */
    public boolean hasExceptions() {
        for (Throwable exception : exceptions) {
            if (exception != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the exception thrown by the executable at the given index.
     *
     * <p>Indices correspond to the original registration order (i.e., the order
     * in which executables were added to the runner).
     *
     * @param index The executable index, in registration order.
     * @return An {@link Optional} containing the exception, or empty if the index
     *         is out of bounds or the executable completed successfully.
     */
    public Optional<Throwable> getException(final int index) {
        if (index < 0 || index >= executableCount) {
            return Optional.empty();
        }
        return Optional.ofNullable(exceptions.get(index));
    }

    /**
     * Returns whether the executable at the given index completed successfully.
     *
     * <p>Indices correspond to the original registration order (i.e., the order
     * in which executables were added to the runner).
     *
     * @param index The executable index, in registration order.
     * @return {@code true} if the executable completed without throwing an exception.
     */
    public boolean isSuccess(final int index) {
        if (index < 0 || index >= executableCount) {
            return false;
        }
        return exceptions.get(index) == null;
    }

    /**
     * Returns all exceptions thrown during execution, keyed by executable index.
     *
     * <p>Keys correspond to the original registration order (i.e., the order
     * in which executables were added to the runner).
     *
     * @return An unmodifiable map of executable index to exception for all failed executables; never null.
     */
    public Map<Integer, Throwable> getExceptionsByIndex() {
        var failures = new HashMap<Integer, Throwable>();
        for (int i = 0; i < exceptions.size(); i++) {
            Throwable exception = exceptions.get(i);
            if (exception != null) {
                failures.put(i, exception);
            }
        }
        return Collections.unmodifiableMap(failures);
    }
}
