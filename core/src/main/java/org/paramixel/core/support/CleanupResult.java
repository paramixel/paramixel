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
 * Describes the outcome of a {@link Cleanup} execution.
 *
 * <p>The result preserves one slot per registered cleanup callback so callers can inspect success or failure by
 * original registration index.
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
     * Returns the number of callbacks that were scheduled for execution.
     *
     * @return the number of registered callbacks
     */
    public int getExecutableCount() {
        return executableCount;
    }

    /**
     * Returns whether any callback produced an exception.
     *
     * @return {@code true} when at least one callback failed
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
     * Returns the exception captured for the callback at the supplied index.
     *
     * @param index the callback index
     * @return the captured exception, or an empty {@link Optional} when the callback succeeded or the index is out of
     *     range
     */
    public Optional<Throwable> getException(final int index) {
        if (index < 0 || index >= executableCount) {
            return Optional.empty();
        }
        return Optional.ofNullable(exceptions.get(index));
    }

    /**
     * Returns whether the callback at the supplied index completed without an exception.
     *
     * @param index the callback index
     * @return {@code true} when the callback succeeded; {@code false} when it failed or the index is out of range
     */
    public boolean isSuccess(final int index) {
        if (index < 0 || index >= executableCount) {
            return false;
        }
        return exceptions.get(index) == null;
    }

    /**
     * Returns every captured failure keyed by callback index.
     *
     * @return an immutable map from callback index to captured throwable
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
