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

package nonapi.org.paramixel.execution;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Bounded, fairly ordered permits for one resource kind: pool capacity or an exclusive lock.
 *
 * <p>The ledger is not thread-safe by design: it is mutated only by the engine thread, which makes acquire, wait, and
 * release atomic with respect to every other scheduling decision and removes a whole class of race. Waiters are
 * ordered by compiled structural priority, so admission is deterministic for an identical ready set.
 *
 * <p>Invariant: for every resource name, {@code inUse <= capacity} always holds, and every successful acquire has
 * exactly one matching release. Any violation is a framework defect, not a test outcome.
 *
 * @param <T> the waiter handle type
 */
final class ResourceLedger<T> {

    private final Map<String, Integer> capacity;
    private final Map<String, Integer> inUse = new HashMap<>();
    private final Map<String, Queue<T>> waiters = new HashMap<>();
    private final Comparator<T> priority;

    /**
     * Creates a ledger.
     *
     * @param capacity the capacity per resource name; must not be {@code null}
     * @param priority the waiter priority order; must not be {@code null}
     */
    ResourceLedger(final Map<String, Integer> capacity, final Comparator<T> priority) {
        this.capacity = Map.copyOf(capacity);
        this.priority = priority;
    }

    /**
     * Attempts to acquire one permit without waiting.
     *
     * @param name the resource name; must not be {@code null}
     * @return {@code true} when the permit was acquired
     */
    boolean tryAcquire(final String name) {
        final var limit = capacity.getOrDefault(name, 0);
        final var current = inUse.getOrDefault(name, 0);
        if (current >= limit) {
            return false;
        }
        inUse.put(name, current + 1);
        return true;
    }

    /**
     * Enqueues a waiter.
     *
     * @param name the resource name; must not be {@code null}
     * @param waiter the waiter; must not be {@code null}
     */
    void await(final String name, final T waiter) {
        waiters.computeIfAbsent(name, key -> new PriorityQueue<>(priority)).add(waiter);
    }

    /**
     * Removes a waiter that no longer wants the permit.
     *
     * @param name the resource name; must not be {@code null}
     * @param waiter the waiter; must not be {@code null}
     * @return {@code true} when the waiter was still queued
     */
    boolean cancelWait(final String name, final T waiter) {
        final var queue = waiters.get(name);
        return queue != null && queue.remove(waiter);
    }

    /**
     * Releases one permit.
     *
     * <p>Releasing does not admit waiters by itself: the caller polls {@link #pollWaiter(String)} and acquires for each
     * waiter it admits. Keeping acquire and release symmetric makes permit conservation directly testable.
     *
     * @param name the resource name; must not be {@code null}
     * @throws IllegalStateException if the permit was not held
     */
    void release(final String name) {
        final var current = inUse.getOrDefault(name, 0);
        if (current <= 0) {
            throw new IllegalStateException("Permit released more often than acquired: " + name);
        }
        inUse.put(name, current - 1);
    }

    /**
     * Removes and returns the highest priority waiter.
     *
     * @param name the resource name; must not be {@code null}
     * @return the waiter, or {@code null} when none is queued
     */
    T pollWaiter(final String name) {
        final var queue = waiters.get(name);
        return queue == null ? null : queue.poll();
    }

    /**
     * Returns whether capacity is available.
     *
     * @param name the resource name; must not be {@code null}
     * @return {@code true} when one more permit can be acquired
     */
    boolean hasCapacity(final String name) {
        return inUse.getOrDefault(name, 0) < capacity.getOrDefault(name, 0);
    }

    /**
     * Returns the number of permits currently held.
     *
     * @param name the resource name; must not be {@code null}
     * @return the number of held permits
     */
    int inUse(final String name) {
        return inUse.getOrDefault(name, 0);
    }

    /**
     * Returns the queued waiters for a resource, for diagnostics.
     *
     * @param name the resource name; must not be {@code null}
     * @return the immutable queued waiters; never {@code null}
     */
    List<T> pending(final String name) {
        final var queue = waiters.get(name);
        return queue == null ? List.of() : List.copyOf(new ArrayDeque<>(queue));
    }
}
