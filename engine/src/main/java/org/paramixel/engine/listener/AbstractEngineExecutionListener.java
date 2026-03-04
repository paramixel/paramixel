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

package org.paramixel.engine.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;

/**
 * Provides common formatting and summary tracking for engine listeners.
 *
 * <p>This base listener supplies ANSI-formatted labels and helper methods used by
 * {@link EngineExecutionListener} implementations in this module.
 *
 * <p><b>Thread safety</b>
 * <p>This type is thread-safe with respect to summary updates. It uses thread-safe counters and
 * concurrent maps inside {@link ExecutionSummary}.
 *
 * @author Douglas Hoard <doug.hoard@gmail.com>
 * @since 0.0.1
 */
public class AbstractEngineExecutionListener implements EngineExecutionListener {

    /**
     * Creates a new listener instance.
     *
     * @since 0.0.1
     */
    protected AbstractEngineExecutionListener() {
        // INTENTIONALLY EMPTY
    }

    /**
     * ANSI escape sequence that resets terminal formatting.
     *
     * @since 0.0.1
     */
    private static final String RESET_ESCAPE_SEQUENCE = "\033[0m";

    /**
     * ANSI-formatted {@code [INFO]} label used for reporting.
     *
     * @since 0.0.1
     */
    protected final String INFO = "[" + "\033[1;34m" + "INFO" + "\033[0m" + "]";

    /**
     * ANSI-formatted {@code TEST} label.
     *
     * @since 0.0.1
     */
    protected final String TEST = "\033[1;37m" + "TEST" + RESET_ESCAPE_SEQUENCE;

    /**
     * ANSI-formatted {@code PASS} label.
     *
     * @since 0.0.1
     */
    protected final String PASS = "\033[1;32m" + "PASS" + RESET_ESCAPE_SEQUENCE;

    /**
     * ANSI-formatted {@code FAIL} label.
     *
     * @since 0.0.1
     */
    protected final String FAIL = "\033[1;31m" + "FAIL" + RESET_ESCAPE_SEQUENCE;

    /**
     * Global execution summary for the current engine run.
     *
     * @since 0.0.1
     */
    protected static final ExecutionSummary EXECUTION_SUMMARY = new ExecutionSummary();

    /**
     * Resets the global execution summary counters.
     *
     * <p>Call this method at the start of an engine execution.
     *
     * @since 0.0.1
     */
    protected void resetExecutionSummary() {
        EXECUTION_SUMMARY.reset();
    }

    /**
     * Returns the global execution summary.
     *
     * @return the global execution summary; never {@code null}
     * @since 0.0.1
     */
    protected ExecutionSummary getExecutionSummary() {
        return EXECUTION_SUMMARY;
    }

    /**
     * Renders a descriptor name path up to the specified depth.
     *
     * <p>This method walks parents from {@code testDescriptor} up to {@code depth} elements and
     * joins display names with {@code " | "}. It stops early when a parent is absent.
     *
     * @param depth the maximum number of hierarchy elements to include; must be {@code >= 1}
     * @param testDescriptor the descriptor to render; never {@code null}
     * @return a formatted display name path; never {@code null}
     * @since 0.0.1
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
     * Performs getStatusMessage.
     *
     * @param testExecutionResult the testExecutionResult
     * @param threadName the threadName
     * @param displayName the displayName
     * @return the result
     * @since 0.0.1
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
     *
     * @author Douglas Hoard <doug.hoard@gmail.com>
     * @since 0.0.1
     */
    protected static final class ExecutionSummary {

        /**
         * Creates a new execution summary instance.
         *
         * @since 0.0.1
         */
        protected ExecutionSummary() {
            // INTENTIONALLY EMPTY
        }

        /**
         * Number of test classes that pass.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testClassPassed = new AtomicInteger(0);

        /**
         * Number of test classes that fail.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testClassFailed = new AtomicInteger(0);

        /**
         * Number of test classes that are skipped or aborted.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testClassSkipped = new AtomicInteger(0);

        /**
         * Number of argument buckets that pass.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testArgumentPassed = new AtomicInteger(0);

        /**
         * Number of argument buckets that fail.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testArgumentFailed = new AtomicInteger(0);

        /**
         * Number of argument buckets that are skipped or aborted.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testArgumentSkipped = new AtomicInteger(0);

        /**
         * Number of test method invocations that pass.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testMethodPassed = new AtomicInteger(0);

        /**
         * Number of test method invocations that fail.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testMethodFailed = new AtomicInteger(0);

        /**
         * Number of test method invocations that are skipped or aborted.
         *
         * @since 0.0.1
         */
        private final AtomicInteger testMethodSkipped = new AtomicInteger(0);

        /**
         * Per-class pass and fail counters keyed by class display name.
         *
         * @since 0.0.1
         */
        private final ConcurrentHashMap<String, ClassStats> classStatsMap = new ConcurrentHashMap<>();

        /**
         * Most recently started class display name.
         *
         * <p>This value is {@code volatile} for safe publication across threads.
         *
         * @since 0.0.1
         */
        private volatile String currentClassName;

        /**
         * Per-class argument bucket count keyed by class display name.
         *
         * @since 0.0.1
         */
        private final ConcurrentHashMap<String, AtomicInteger> classArgumentCounts = new ConcurrentHashMap<>();

        /**
         * Per-class method count keyed by class display name.
         *
         * @since 0.0.1
         */
        private final ConcurrentHashMap<String, AtomicInteger> classMethodCounts = new ConcurrentHashMap<>();

        /**
         * Resets all counters and tracked class state.
         *
         * <p>This method clears concurrent maps and sets counters to zero.
         *
         * @since 0.0.1
         */
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

        /**
         * Sets the current class name and ensures per-class state exists.
         *
         * @param className the class display name; may be {@code null}
         * @since 0.0.1
         */
        protected void setCurrentClassName(final String className) {
            this.currentClassName = className;
            classStatsMap.computeIfAbsent(className, k -> new ClassStats(className));
        }

        /**
         * Returns the current class name.
         *
         * @return the current class display name, or {@code null} when unset
         * @since 0.0.1
         */
        protected String getCurrentClassName() {
            return currentClassName;
        }

        /**
         * Increments the argument bucket count for a class.
         *
         * @param className the class display name; never {@code null}
         * @since 0.0.1
         */
        protected void incrementClassArgumentCount(final String className) {
            classArgumentCounts
                    .computeIfAbsent(className, k -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        /**
         * Increments the method count for a class.
         *
         * @param className the class display name; never {@code null}
         * @since 0.0.1
         */
        protected void incrementClassMethodCount(final String className) {
            classMethodCounts
                    .computeIfAbsent(className, k -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        /**
         * Increments the per-class passed count.
         *
         * @param className the class display name; never {@code null}
         * @since 0.0.1
         */
        protected void incrementClassPassed(final String className) {
            ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats(className));
            stats.passed.incrementAndGet();
        }

        /**
         * Increments the per-class failed count.
         *
         * @param className the class display name; never {@code null}
         * @since 0.0.1
         */
        protected void incrementClassFailed(final String className) {
            ClassStats stats = classStatsMap.computeIfAbsent(className, k -> new ClassStats(className));
            stats.failed.incrementAndGet();
        }

        /**
         * Increments the global count of passed test classes.
         *
         * @since 0.0.1
         */
        protected void incrementTestClassPassed() {
            testClassPassed.incrementAndGet();
        }

        /**
         * Increments the global count of failed test classes.
         *
         * @since 0.0.1
         */
        protected void incrementTestClassFailed() {
            testClassFailed.incrementAndGet();
        }

        /**
         * Increments the global count of skipped/aborted test classes.
         *
         * @since 0.0.1
         */
        protected void incrementTestClassSkipped() {
            testClassSkipped.incrementAndGet();
        }

        /**
         * Increments the global count of passed argument buckets.
         *
         * @since 0.0.1
         */
        protected void incrementTestArgumentPassed() {
            testArgumentPassed.incrementAndGet();
        }

        /**
         * Increments the global count of failed argument buckets.
         *
         * @since 0.0.1
         */
        protected void incrementTestArgumentFailed() {
            testArgumentFailed.incrementAndGet();
        }

        /**
         * Increments the global count of skipped/aborted argument buckets.
         *
         * @since 0.0.1
         */
        protected void incrementTestArgumentSkipped() {
            testArgumentSkipped.incrementAndGet();
        }

        /**
         * Increments the global count of passed test methods.
         *
         * @since 0.0.1
         */
        protected void incrementTestMethodPassed() {
            testMethodPassed.incrementAndGet();
        }

        /**
         * Increments the global count of failed test methods.
         *
         * @since 0.0.1
         */
        protected void incrementTestMethodFailed() {
            testMethodFailed.incrementAndGet();
        }

        /**
         * Increments the global count of skipped/aborted test methods.
         *
         * @since 0.0.1
         */
        protected void incrementTestMethodSkipped() {
            testMethodSkipped.incrementAndGet();
        }

        /**
         * Returns the number of passed test classes.
         *
         * @return passed class count
         * @since 0.0.1
         */
        protected int getTestClassPassed() {
            return testClassPassed.get();
        }

        /**
         * Returns the number of failed test classes.
         *
         * @return failed class count
         * @since 0.0.1
         */
        protected int getTestClassFailed() {
            return testClassFailed.get();
        }

        /**
         * Returns the number of skipped/aborted test classes.
         *
         * @return skipped class count
         * @since 0.0.1
         */
        protected int getTestClassSkipped() {
            return testClassSkipped.get();
        }

        /**
         * Returns the number of passed argument buckets.
         *
         * @return passed argument count
         * @since 0.0.1
         */
        protected int getTestArgumentPassed() {
            return testArgumentPassed.get();
        }

        /**
         * Returns the number of failed argument buckets.
         *
         * @return failed argument count
         * @since 0.0.1
         */
        protected int getTestArgumentFailed() {
            return testArgumentFailed.get();
        }

        /**
         * Returns the number of skipped/aborted argument buckets.
         *
         * @return skipped argument count
         * @since 0.0.1
         */
        protected int getTestArgumentSkipped() {
            return testArgumentSkipped.get();
        }

        /**
         * Returns the number of passed test methods.
         *
         * @return passed method count
         * @since 0.0.1
         */
        protected int getTestMethodPassed() {
            return testMethodPassed.get();
        }

        /**
         * Returns the number of failed test methods.
         *
         * @return failed method count
         * @since 0.0.1
         */
        protected int getTestMethodFailed() {
            return testMethodFailed.get();
        }

        /**
         * Returns the number of skipped/aborted test methods.
         *
         * @return skipped method count
         * @since 0.0.1
         */
        protected int getTestMethodSkipped() {
            return testMethodSkipped.get();
        }

        /**
         * Returns the per-class statistics map.
         *
         * @return the per-class statistics map; never {@code null}
         * @since 0.0.1
         */
        protected ConcurrentHashMap<String, ClassStats> getClassStatsMap() {
            return classStatsMap;
        }

        /**
         * Returns the number of argument buckets observed for a class.
         *
         * @param className the class display name; never {@code null}
         * @return the argument count, or {@code 0} when no data exists
         * @since 0.0.1
         */
        protected int getClassArgumentCount(final String className) {
            AtomicInteger count = classArgumentCounts.get(className);
            return count != null ? count.get() : 0;
        }

        /**
         * Returns the number of test methods observed for a class.
         *
         * @param className the class display name; never {@code null}
         * @return the method count, or {@code 0} when no data exists
         * @since 0.0.1
         */
        protected int getClassMethodCount(final String className) {
            AtomicInteger count = classMethodCounts.get(className);
            return count != null ? count.get() : 0;
        }

        /**
         * Statistics for a single test class.
         *
         * @author Douglas Hoard <doug.hoard@gmail.com>
         * @since 0.0.1
         */
        protected static final class ClassStats {

            /**
             * Class display name; immutable.
             *
             * @since 0.0.1
             */
            final String className;

            /**
             * Passed method count for this class; mutable and thread-safe.
             *
             * @since 0.0.1
             */
            final AtomicInteger passed = new AtomicInteger(0);

            /**
             * Failed method count for this class; mutable and thread-safe.
             *
             * @since 0.0.1
             */
            final AtomicInteger failed = new AtomicInteger(0);

            /**
             * Creates statistics for a class.
             *
             * @param className the class display name; never {@code null}
             * @since 0.0.1
             */
            ClassStats(final String className) {
                this.className = className;
            }

            /**
             * Returns the total number of completed methods.
             *
             * @return passed + failed
             * @since 0.0.1
             */
            int getTotal() {
                return passed.get() + failed.get();
            }
        }
    }
}
