/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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
 * Limits engine concurrency using fair semaphores.
 *
 * <p>This limiter enforces three categories of permits:
 * <ul>
 *   <li><b>Total slots</b>: hard global cap on parallel work ({@code cores * 2})</li>
 *   <li><b>Class slots</b>: cap on concurrently running test classes ({@code cores})</li>
 *   <li><b>Argument slots</b>: cap on additional parallel work ({@code cores})</li>
 * </ul>
 *
 * <p><b>Fairness</b>
 * <p>All semaphores are constructed as fair (FIFO) to reduce starvation.
 *
 * <p><b>Thread safety</b>
 * <p>This type is thread-safe.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class ParamixelConcurrencyLimiter {

    /**
     * Core count used to size permit pools.
     *
     * <p>This value is immutable and is always {@code >= 1}.
     */
    private final int cores;

    /**
     * Total permit pool that caps all parallel work.
     */
    private final Semaphore totalSlots;

    /**
     * Permit pool that caps concurrently active test classes.
     */
    private final Semaphore classSlots;

    /**
     * Permit pool that caps extra parallel work within a class.
     */
    private final Semaphore argumentSlots;

    /**
     * Creates a limiter sized by core count.
     *
     * @param cores the core count; must be {@code >= 1}
     * @throws IllegalArgumentException if {@code cores < 1}
     * @since 0.0.1
     */
    public ParamixelConcurrencyLimiter(final int cores) {
        if (cores < 1) {
            throw new IllegalArgumentException("cores must be >= 1");
        }
        this.cores = cores;
        this.totalSlots = new Semaphore(cores * 2, true);
        this.classSlots = new Semaphore(cores, true);
        this.argumentSlots = new Semaphore(cores, true);
    }

    /**
     * Returns the configured core count.
     *
     * @return the core count; always {@code >= 1}
     * @since 0.0.1
     */
    public int cores() {
        return cores;
    }

    /**
     * Returns the configured total slot capacity.
     *
     * @return the total slot capacity
     * @since 0.0.1
     */
    public int totalSlots() {
        return cores * 2;
    }

    /**
     * Returns the configured class slot capacity.
     *
     * @return the class slot capacity
     * @since 0.0.1
     */
    public int classSlots() {
        return cores;
    }

    /**
     * Returns the configured argument slot capacity.
     *
     * @return the argument slot capacity
     * @since 0.0.1
     */
    public int argumentSlots() {
        return cores;
    }

    /**
     * Returns the number of total slots currently in use.
     *
     * @return the number of total slots acquired
     * @since 0.0.1
     */
    public int totalSlotsInUse() {
        return totalSlots() - totalSlots.availablePermits();
    }

    /**
     * Returns the number of class slots currently in use.
     *
     * @return the number of class slots acquired
     * @since 0.0.1
     */
    public int classSlotsInUse() {
        return classSlots() - classSlots.availablePermits();
    }

    /**
     * Returns the number of argument slots currently in use.
     *
     * @return the number of argument slots acquired
     * @since 0.0.1
     */
    public int argumentSlotsInUse() {
        return argumentSlots() - argumentSlots.availablePermits();
    }

    /**
     * Acquires the permits required to start a test class.
     *
     * <p>This is a blocking acquire (FIFO, fair).
     *
     * @return a permit that releases the acquired capacity on {@link ClassPermit#close()}; never {@code null}
     * @throws InterruptedException if the current thread is interrupted while acquiring permits
     * @since 0.0.1
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
                // INTENTIONALLY EMPTY
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
     *
     * @return an acquired permit, or {@link Optional#empty()} when capacity is unavailable
     * @since 0.0.1
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

    /**
     * Releases permits associated with a class execution.
     *
     * <p>This method is private because only {@link ClassPermit} should release permits.
     *
     * @since 0.0.1
     */
    private void releaseClassExecution() {
        totalSlots.release();
        classSlots.release();
    }

    /**
     * Releases permits associated with an argument execution.
     *
     * <p>This method is private because only {@link ArgumentPermit} should release permits.
     *
     * @since 0.0.1
     */
    private void releaseArgumentExecution() {
        argumentSlots.release();
        totalSlots.release();
    }

    /**
     * Permit representing acquired class execution capacity.
     *
     * <p>This type is public to support try-with-resources usage by callers.
     *
     * <p><b>Thread safety</b>
     * <p>This permit is thread-safe and idempotent.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     * @since 0.0.1
     */
    public static final class ClassPermit implements AutoCloseable {

        /**
         * Owning limiter used to release permits; immutable.
         */
        private final ParamixelConcurrencyLimiter limiter;

        /**
         * Guard that prevents double-release; mutable and thread-safe.
         */
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * Creates a permit for the given limiter.
         *
         * @param limiter the owning limiter; never {@code null}
         * @since 0.0.1
         */
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

    /**
     * Permit representing acquired argument execution capacity.
     *
     * <p>This type is public to support try-with-resources usage by callers.
     *
     * <p><b>Thread safety</b>
     * <p>This permit is thread-safe and idempotent.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     * @since 0.0.1
     */
    public static final class ArgumentPermit implements AutoCloseable {

        /**
         * Owning limiter used to release permits; immutable.
         */
        private final ParamixelConcurrencyLimiter limiter;

        /**
         * Guard that prevents double-release; mutable and thread-safe.
         */
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * Creates a permit for the given limiter.
         *
         * @param limiter the owning limiter; never {@code null}
         * @since 0.0.1
         */
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
