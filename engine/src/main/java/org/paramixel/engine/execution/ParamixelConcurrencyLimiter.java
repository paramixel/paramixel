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

package org.paramixel.engine.execution;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Semaphore-based concurrency limiter for Paramixel engine execution.
 *
 * <p>Slots:
 * <ul>
 *   <li>totalSlots = cores * 2 (hard global cap on parallel work)</li>
 *   <li>classSlots = cores (cap on concurrently running test classes)</li>
 *   <li>argumentSlots = cores (cap on extra parallel work beyond class ownership)</li>
 * </ul>
 */
public final class ParamixelConcurrencyLimiter {

    private final int cores;

    private final Semaphore totalSlots;
    private final Semaphore classSlots;
    private final Semaphore argumentSlots;

    public ParamixelConcurrencyLimiter(final int cores) {
        if (cores < 1) {
            throw new IllegalArgumentException("cores must be >= 1");
        }
        this.cores = cores;
        this.totalSlots = new Semaphore(cores * 2, true);
        this.classSlots = new Semaphore(cores, true);
        this.argumentSlots = new Semaphore(cores, true);
    }

    public int cores() {
        return cores;
    }

    public int totalSlots() {
        return cores * 2;
    }

    public int classSlots() {
        return cores;
    }

    public int argumentSlots() {
        return cores;
    }

    public int totalSlotsInUse() {
        return totalSlots() - totalSlots.availablePermits();
    }

    public int classSlotsInUse() {
        return classSlots() - classSlots.availablePermits();
    }

    public int argumentSlotsInUse() {
        return argumentSlots() - argumentSlots.availablePermits();
    }

    /**
     * Acquires the permits required to start a test class.
     *
     * <p>This is a blocking acquire (FIFO, fair).
     */
    public ClassPermit acquireClassExecution() throws InterruptedException {
        classSlots.acquire();
        boolean totalAcquired = false;
        try {
            totalSlots.acquire();
            totalAcquired = true;
            return new ClassPermit(this);
        } catch (InterruptedException e) {
            if (!totalAcquired) {
                // no-op
            }
            classSlots.release();
            throw e;
        } catch (RuntimeException e) {
            classSlots.release();
            throw e;
        }
    }

    /**
     * Attempts to acquire permits for extra parallel work beyond class ownership.
     *
     * <p>Never blocks: if no capacity exists, returns empty and the caller must
     * execute inline to preserve progress.
     */
    public Optional<ArgumentPermit> tryAcquireArgumentExecution() {
        if (!totalSlots.tryAcquire()) {
            return Optional.empty();
        }
        if (!argumentSlots.tryAcquire()) {
            totalSlots.release();
            return Optional.empty();
        }
        return Optional.of(new ArgumentPermit(this));
    }

    private void releaseClassExecution() {
        totalSlots.release();
        classSlots.release();
    }

    private void releaseArgumentExecution() {
        argumentSlots.release();
        totalSlots.release();
    }

    public static final class ClassPermit implements AutoCloseable {
        private final ParamixelConcurrencyLimiter limiter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ClassPermit(final ParamixelConcurrencyLimiter limiter) {
            this.limiter = limiter;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                limiter.releaseClassExecution();
            }
        }
    }

    public static final class ArgumentPermit implements AutoCloseable {
        private final ParamixelConcurrencyLimiter limiter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ArgumentPermit(final ParamixelConcurrencyLimiter limiter) {
            this.limiter = limiter;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                limiter.releaseArgumentExecution();
            }
        }
    }
}
