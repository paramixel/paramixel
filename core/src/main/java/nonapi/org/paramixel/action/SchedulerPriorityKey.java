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

package nonapi.org.paramixel.action;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable scheduler ordering key derived from a descriptor's tree path.
 *
 * <p>Ordering is lexicographic by child index, with continuation-first behavior
 * when one path is a prefix of another (deeper path first).</p>
 */
public final class SchedulerPriorityKey implements Comparable<SchedulerPriorityKey> {

    private static final SchedulerPriorityKey ROOT = new SchedulerPriorityKey();
    private static final int ARRAY_CACHE_SIZE = 16;

    private final int[] path;
    private final SchedulerPriorityKey[] arrayCache = new SchedulerPriorityKey[ARRAY_CACHE_SIZE];
    private volatile SchedulerPriorityKey[] overflowCache;

    private SchedulerPriorityKey(final int... path) {
        this.path = path;
    }

    /**
     * Returns the root priority key (empty path).
     *
     * @return the root key; never {@code null}
     */
    public static SchedulerPriorityKey root() {
        return ROOT;
    }

    /**
     * Returns a new key for a child at {@code childIndex}.
     *
     * <p>Child keys are cached for reuse. The same key is returned for the same
     * {@code childIndex} on subsequent calls, eliminating redundant allocations
     * during descriptor tree construction.</p>
     *
     * @param childIndex the child index in parent order (must be non-negative)
     * @return child key; never {@code null}
     */
    public SchedulerPriorityKey child(final int childIndex) {
        if (childIndex < 0) {
            throw new IllegalArgumentException("childIndex must be non-negative, was: " + childIndex);
        }
        if (childIndex < ARRAY_CACHE_SIZE) {
            var cached = arrayCache[childIndex];
            if (cached != null) {
                return cached;
            }
            var childPath = Arrays.copyOf(path, path.length + 1);
            childPath[path.length] = childIndex;
            var key = new SchedulerPriorityKey(childPath);
            arrayCache[childIndex] = key;
            return key;
        }
        return computeOverflow(childIndex);
    }

    private synchronized SchedulerPriorityKey computeOverflow(final int childIndex) {
        if (overflowCache == null) {
            overflowCache = new SchedulerPriorityKey[childIndex + 1];
        } else if (childIndex >= overflowCache.length) {
            overflowCache = Arrays.copyOf(overflowCache, childIndex + 1);
        }
        var cached = overflowCache[childIndex];
        if (cached != null) {
            return cached;
        }
        var childPath = Arrays.copyOf(path, path.length + 1);
        childPath[path.length] = childIndex;
        var key = new SchedulerPriorityKey(childPath);
        overflowCache[childIndex] = key;
        return key;
    }

    @Override
    public int compareTo(final SchedulerPriorityKey other) {
        Objects.requireNonNull(other, "other is null");
        var size = Math.min(path.length, other.path.length);
        for (var i = 0; i < size; i++) {
            var comparison = Integer.compare(path[i], other.path[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(other.path.length, path.length);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SchedulerPriorityKey schedulerPriorityKey)) {
            return false;
        }
        return Arrays.equals(path, schedulerPriorityKey.path);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(path);
    }
}
