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

package nonapi.org.paramixel;

import java.util.concurrent.Semaphore;

/**
 * One named concurrency budget: a fair counting semaphore of fixed size.
 *
 * <p>A {@code Parallel} node acquires one permit per child before the child starts and the child's own thread
 * releases it when the child's subtree is terminal, so at most {@link #size()} children of all nodes sharing the
 * pool are alive at once. Acquisition is interruptible and FIFO.
 */
final class Pool {

    private final String name;
    private final int size;
    private final Semaphore permits;
    private final java.util.concurrent.atomic.AtomicInteger peakInUse = new java.util.concurrent.atomic.AtomicInteger();

    Pool(final String name, final int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("pool size must be positive, was: " + size);
        }
        this.name = name;
        this.size = size;
        this.permits = new Semaphore(size, true);
    }

    String name() {
        return name;
    }

    int size() {
        return size;
    }

    /**
     * Blocks until a permit is available.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    void acquire() throws InterruptedException {
        permits.acquire();
        var inUse = size - permits.availablePermits();
        peakInUse.accumulateAndGet(inUse, Math::max);
    }

    /**
     * Returns a permit taken by {@link #acquire()}.
     */
    void release() {
        permits.release();
    }

    /**
     * Returns the number of free permits.
     *
     * @return the free permit count
     */
    int availablePermits() {
        return permits.availablePermits();
    }

    /**
     * Returns the highest number of permits that were in use at the same time.
     *
     * <p>"Why was this slow?" is the most common question about a parallel run, and peak usage answers it: a pool
     * whose peak equals its size was saturated, and a pool whose peak is one serialized the work.
     *
     * @return the peak in-use count, between zero and {@link #size()}
     */
    int peakInUse() {
        return peakInUse.get();
    }
}
