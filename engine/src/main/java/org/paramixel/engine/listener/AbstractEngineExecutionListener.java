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

package org.paramixel.engine.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;

/**
 * Base listener with common formatting utilities for execution reporting.
 */
public class AbstractEngineExecutionListener implements EngineExecutionListener {

    /**
     * ANSI reset escape sequence for restoring console colors.
     */
    private static final String RESET_ESCAPE_SEQUENCE = "\033[0m";

    /**
     * ANSI-formatted info label.
     */
    protected final String INFO = "[" + "\033[1;34m" + "INFO" + "\033[0m" + "]";

    /**
     * ANSI-formatted test label.
     */
    protected final String TEST = "\033[1;37m" + "TEST" + RESET_ESCAPE_SEQUENCE;

    /**
     * ANSI-formatted pass label.
     */
    protected final String PASS = "\033[1;32m" + "PASS" + RESET_ESCAPE_SEQUENCE;

    /**
     * ANSI-formatted fail label.
     */
    protected final String FAIL = "\033[1;31m" + "FAIL" + RESET_ESCAPE_SEQUENCE;

    /**
     * Execution summary counters for the current engine run.
     */
    protected static final ExecutionSummary EXECUTION_SUMMARY = new ExecutionSummary();

    /**
     * Resets execution summary counters.
     */
    protected void resetExecutionSummary() {
        EXECUTION_SUMMARY.reset();
    }

    /**
     * Returns the execution summary counters.
     *
     * @return the execution summary
     */
    protected ExecutionSummary getExecutionSummary() {
        return EXECUTION_SUMMARY;
    }

    /**
     * Builds a human-readable display name from a descriptor hierarchy.
     *
     * @param depth the maximum depth to include
     * @param testDescriptor the descriptor to render
     * @return a formatted display name
     */
    protected String getDisplayName(final int depth, final @NonNull TestDescriptor testDescriptor) {
        List<String> names = new ArrayList<>();
        TestDescriptor currentTestDescriptor = testDescriptor;

        while (currentTestDescriptor != null && names.size() < depth) {
            names.add(currentTestDescriptor.getDisplayName());
            currentTestDescriptor = currentTestDescriptor.getParent().orElse(null);
        }

        Collections.reverse(names);

        StringBuilder displayName = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            displayName.append(names.get(i));
            if (i < names.size() - 1) {
                displayName.append(" | ");
            }
        }

        return displayName.toString();
    }

    /**
     * Builds a status message for the given execution result.
     *
     * @param testExecutionResult the execution result
     * @param threadName the executing thread name
     * @param displayName the test display name
     * @return a formatted status message
     */
    protected String getStatusMessage(
            final @NonNull TestExecutionResult testExecutionResult,
            final @NonNull String threadName,
            final @NonNull String displayName) {
        String message;

        switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL: {
                message = INFO + " " + PASS + " | " + threadName + " | " + displayName;
                break;
            }
            case ABORTED: {
                message = INFO + " " + threadName + " | ABORTED | " + displayName;
                break;
            }
            case FAILED: {
                message = INFO + " " + threadName + " | FAIL | " + displayName;
                break;
            }
            default: {
                message = INFO + " " + threadName + " | UNKNOWN | " + displayName;
            }
        }

        return message;
    }

    /**
     * Execution summary counters.
     */
    protected static final class ExecutionSummary {

        private final java.util.concurrent.atomic.AtomicInteger testClassPassed =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger testClassFailed =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger testClassSkipped =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger testArgumentPassed =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger testArgumentFailed =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger testArgumentSkipped =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger testMethodPassed =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger testMethodFailed =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicInteger testMethodSkipped =
                new java.util.concurrent.atomic.AtomicInteger(0);

        /**
         * Per-class statistics map keyed by class name.
         */
        private final ConcurrentHashMap<String, ClassStats> classStatsMap = new ConcurrentHashMap<>();

        /**
         * Tracks the current class being executed.
         */
        private volatile String currentClassName;

        /**
         * Tracks the number of arguments for the current class.
         */
        private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> classArgumentCounts =
                new ConcurrentHashMap<>();

        /**
         * Tracks the number of methods for the current class.
         */
        private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> classMethodCounts =
                new ConcurrentHashMap<>();

        protected void reset() {
            testClassPassed.set(0);
            testClassFailed.set(0);
            testClassSkipped.set(0);
            testArgumentPassed.set(0);
            testArgumentFailed.set(0);
            testArgumentSkipped.set(0);
            testMethodPassed.set(0);
            testMethodFailed.set(0);
            testMethodSkipped.set(0);
            classStatsMap.clear();
            classArgumentCounts.clear();
            classMethodCounts.clear();
            currentClassName = null;
        }

        protected void setCurrentClassName(final String className) {
            this.currentClassName = className;
            classStatsMap.computeIfAbsent(className, k -> new ClassStats(className));
        }

        protected String getCurrentClassName() {
            return currentClassName;
        }

        protected void incrementClassArgumentCount(final String className) {
            classArgumentCounts
                    .computeIfAbsent(className, k -> new java.util.concurrent.atomic.AtomicInteger(0))
                    .incrementAndGet();
        }

        protected void incrementClassMethodCount(final String className) {
            classMethodCounts
                    .computeIfAbsent(className, k -> new java.util.concurrent.atomic.AtomicInteger(0))
                    .incrementAndGet();
        }

        protected void incrementClassPassed(final String className) {
            ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats(className));
            stats.passed.incrementAndGet();
        }

        protected void incrementClassFailed(final String className) {
            ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats(className));
            stats.failed.incrementAndGet();
        }

        protected void incrementTestClassPassed() {
            testClassPassed.incrementAndGet();
        }

        protected void incrementTestClassFailed() {
            testClassFailed.incrementAndGet();
        }

        protected void incrementTestClassSkipped() {
            testClassSkipped.incrementAndGet();
        }

        protected void incrementTestArgumentPassed() {
            testArgumentPassed.incrementAndGet();
        }

        protected void incrementTestArgumentFailed() {
            testArgumentFailed.incrementAndGet();
        }

        protected void incrementTestArgumentSkipped() {
            testArgumentSkipped.incrementAndGet();
        }

        protected void incrementTestMethodPassed() {
            testMethodPassed.incrementAndGet();
        }

        protected void incrementTestMethodFailed() {
            testMethodFailed.incrementAndGet();
        }

        protected void incrementTestMethodSkipped() {
            testMethodSkipped.incrementAndGet();
        }

        protected int getTestClassPassed() {
            return testClassPassed.get();
        }

        protected int getTestClassFailed() {
            return testClassFailed.get();
        }

        protected int getTestClassSkipped() {
            return testClassSkipped.get();
        }

        protected int getTestArgumentPassed() {
            return testArgumentPassed.get();
        }

        protected int getTestArgumentFailed() {
            return testArgumentFailed.get();
        }

        protected int getTestArgumentSkipped() {
            return testArgumentSkipped.get();
        }

        protected int getTestMethodPassed() {
            return testMethodPassed.get();
        }

        protected int getTestMethodFailed() {
            return testMethodFailed.get();
        }

        protected int getTestMethodSkipped() {
            return testMethodSkipped.get();
        }

        protected ConcurrentHashMap<String, ClassStats> getClassStatsMap() {
            return classStatsMap;
        }

        protected int getClassArgumentCount(final String className) {
            java.util.concurrent.atomic.AtomicInteger count = classArgumentCounts.get(className);
            return count != null ? count.get() : 0;
        }

        protected int getClassMethodCount(final String className) {
            java.util.concurrent.atomic.AtomicInteger count = classMethodCounts.get(className);
            return count != null ? count.get() : 0;
        }

        /**
         * Statistics for a single test class.
         */
        protected static final class ClassStats {
            final String className;
            final java.util.concurrent.atomic.AtomicInteger passed = new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger(0);

            ClassStats(final String className) {
                this.className = className;
            }

            int getTotal() {
                return passed.get() + failed.get();
            }
        }
    }
}
