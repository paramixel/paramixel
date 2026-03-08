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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Tracks completion of execution tasks.
 *
 * <p>This tracker monitors the completion of classes and their arguments,
 * providing synchronization for cleanup and finalization.
 *
 * <p><b>Thread safety</b>
 * <p>This class is thread-safe.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class CompletionTracker {

    /**
     * Logger used for lifecycle and tracking diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(CompletionTracker.class.getName());

    /**
     * Default timeout for completion waits.
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    /**
     * Map tracking completion status per class.
     */
    private final Map<Class<?>, ClassCompletionInfo> completionInfo;

    /**
     * Creates a new completion tracker.
     */
    public CompletionTracker() {
        this.completionInfo = new HashMap<>();
    }

    /**
     * Registers a test class with the specified number of arguments.
     *
     * @param testClass the test class; never {@code null}
     * @param argumentCount the number of arguments for this class
     */
    public synchronized void registerClass(final Class<?> testClass, final int argumentCount) {
        if (completionInfo.containsKey(testClass)) {
            throw new IllegalStateException("Class already registered: " + testClass.getName());
        }
        completionInfo.put(testClass, new ClassCompletionInfo(argumentCount));
        LOGGER.fine("Registered class " + testClass.getName() + " with " + argumentCount + " arguments");
    }

    /**
     * Marks an argument as completed for the specified class.
     *
     * @param testClass the test class; never {@code null}
     * @param argumentIndex the index of the completed argument
     * @param failure the failure that occurred, or {@code null} if successful
     */
    public synchronized void markArgumentCompleted(
            final Class<?> testClass, final int argumentIndex, final Throwable failure) {
        final ClassCompletionInfo info = completionInfo.get(testClass);
        if (info == null) {
            throw new IllegalStateException("Class not registered: " + testClass.getName());
        }

        info.markArgumentCompleted(argumentIndex, failure);
        LOGGER.fine("Argument completed for class " + testClass.getName() + " index=" + argumentIndex);
    }

    /**
     * Waits for all arguments of the specified class to complete.
     *
     * @param testClass the test class; never {@code null}
     * @return the first failure encountered, or {@code null} if all succeeded
     * @throws InterruptedException if the wait is interrupted
     */
    public synchronized Throwable waitForClassCompletion(final Class<?> testClass) throws InterruptedException {
        final ClassCompletionInfo info = completionInfo.get(testClass);
        if (info == null) {
            throw new IllegalStateException("Class not registered: " + testClass.getName());
        }

        if (!info.completionLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            LOGGER.warning("Timeout waiting for class completion: " + testClass.getName());
            return new RuntimeException("Timeout waiting for class completion");
        }

        return info.getFirstFailure();
    }

    /**
     * Returns whether all arguments for the specified class have completed.
     *
     * @param testClass the test class; never {@code null}
     * @return {@code true} if all arguments have completed, {@code false} otherwise
     */
    public synchronized boolean isClassCompleted(final Class<?> testClass) {
        final ClassCompletionInfo info = completionInfo.get(testClass);
        return info != null && info.completionLatch.getCount() == 0;
    }

    /**
     * Removes completion tracking for the specified class.
     *
     * @param testClass the test class; never {@code null}
     */
    public synchronized void unregisterClass(final Class<?> testClass) {
        completionInfo.remove(testClass);
        LOGGER.fine("Unregistered class " + testClass.getName());
    }

    /**
     * Internal class for tracking completion per test class.
     */
    private static final class ClassCompletionInfo {

        /**
         * Latch that counts down as arguments complete.
         */
        final CountDownLatch completionLatch;

        /**
         * Failure that occurred during execution.
         */
        private volatile Throwable firstFailure = null;

        /**
         * Creates a new completion info for a class.
         *
         * @param argumentCount the number of arguments to track
         */
        ClassCompletionInfo(final int argumentCount) {
            this.completionLatch = new CountDownLatch(argumentCount);
        }

        /**
         * Marks an argument as completed.
         *
         * @param argumentIndex the argument index
         * @param failure the failure that occurred, or {@code null} if successful
         */
        synchronized void markArgumentCompleted(final int argumentIndex, final Throwable failure) {
            if (failure != null && firstFailure == null) {
                firstFailure = failure;
            }
            completionLatch.countDown();
        }

        /**
         * Returns the first failure encountered.
         *
         * @return the first failure, or {@code null} if none
         */
        Throwable getFirstFailure() {
            return firstFailure;
        }
    }

    /**
     * Statistics for monitoring execution progress.
     */
    public static final class Statistics {

        /**
         * Number of classes being tracked.
         */
        private final AtomicInteger classCount = new AtomicInteger(0);

        /**
         * Number of arguments being tracked.
         */
        private final AtomicInteger argumentCount = new AtomicInteger(0);

        /**
         * Number of completed arguments.
         */
        private final AtomicInteger completedArguments = new AtomicInteger(0);

        /**
         * Records a class registration.
         *
         * @param argumentCount the number of arguments in the class
         */
        public void registerClass(final int argumentCount) {
            classCount.incrementAndGet();
            this.argumentCount.addAndGet(argumentCount);
        }

        /**
         * Records an argument completion.
         */
        public void markArgumentCompleted() {
            completedArguments.incrementAndGet();
        }

        /**
         * Returns the number of classes being tracked.
         *
         * @return the class count
         */
        public int getClassCount() {
            return classCount.get();
        }

        /**
         * Returns the total number of arguments being tracked.
         *
         * @return the argument count
         */
        public int getArgumentCount() {
            return argumentCount.get();
        }

        /**
         * Returns the number of completed arguments.
         *
         * @return the completed argument count
         */
        public int getCompletedArguments() {
            return completedArguments.get();
        }

        /**
         * Returns the completion percentage.
         *
         * @return a value between 0.0 and 100.0
         */
        public double getCompletionPercentage() {
            final int total = getArgumentCount();
            if (total == 0) {
                return 100.0;
            }
            return (getCompletedArguments() * 100.0) / total;
        }
    }
}
