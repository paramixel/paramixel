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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.engine.util.DurationUtils;

/**
 * Reports a summarized execution report for the engine root descriptor.
 *
 * <p>This listener resets the shared execution summary at engine start and prints an aggregated
 * report at engine finish.
 *
 * <p><b>Thread safety</b>
 * <p>This class is not thread-safe for concurrent engine executions because it stores
 * {@code startTimeMillis} as mutable state. The engine constructs a new listener per execution.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class ParamixelEngineDescriptorEngineExecutionListener extends AbstractEngineExecutionListener {

    /**
     * Printer used for emitting the execution report.
     */
    private final Consumer<String> printer;

    /**
     * Start time for the current engine execution in epoch milliseconds.
     */
    private long startTimeMillis;

    /**
     * Creates a listener that writes the report to the provided printer.
     *
     * @param printer the printer to receive report lines; never {@code null}
     * @since 0.0.1
     */
    public ParamixelEngineDescriptorEngineExecutionListener(final @NonNull Consumer<String> printer) {
        this.printer = Objects.requireNonNull(printer, "printer must not be null");
    }

    @Override
    public void executionStarted(final @NonNull TestDescriptor testDescriptor) {
        startTimeMillis = System.currentTimeMillis();
        resetExecutionSummary();
        printer.accept(INFO + " Paramixel starting...");
    }

    @Override
    public void executionFinished(
            final @NonNull TestDescriptor testDescriptor, final @NonNull TestExecutionResult testExecutionResult) {
        long durationMillis = System.currentTimeMillis() - startTimeMillis;
        String duration = DurationUtils.formatMillis(durationMillis);

        ExecutionSummary summary = getExecutionSummary();

        // Print header
        printLine(INFO + " ╔══════════════════════════════════════════════════════════════╗");
        printLine(INFO + " ║                    Paramixel Test Summary                    ║");
        printLine(INFO + " ╠══════════════════════════════════════════════════════════════╣");
        String durationContent = " Duration: " + duration;
        int contentWidth = 62;
        int padding = contentWidth - durationContent.length();
        StringBuilder durationLine = new StringBuilder();
        durationLine.append("║");
        durationLine.append(durationContent);
        durationLine.append(" ".repeat(Math.max(0, padding)));
        durationLine.append("║");
        printLine(INFO + " " + durationLine);
        printLine(INFO + " ╚══════════════════════════════════════════════════════════════╝");
        printer.accept(INFO);

        // Print hierarchical summary
        int totalClasses = summary.getTestClassPassed() + summary.getTestClassFailed() + summary.getTestClassSkipped();
        int totalArguments =
                summary.getTestArgumentPassed() + summary.getTestArgumentFailed() + summary.getTestArgumentSkipped();
        int totalMethods =
                summary.getTestMethodPassed() + summary.getTestMethodFailed() + summary.getTestMethodSkipped();

        printer.accept(INFO + " " + totalClasses + " Classes tested");
        printer.accept(INFO + " └── " + totalArguments + " Arguments tested");
        printer.accept(INFO + "     └── " + totalMethods + " Test methods tested");
        printer.accept(INFO);

        // Build class breakdown table
        ConcurrentHashMap<String, ExecutionSummary.ClassStats> classStatsMap = summary.getClassStatsMap();
        List<String> classNames = new ArrayList<>(classStatsMap.keySet());
        classNames.sort(String::compareTo);

        // Initialize totals
        int totalArgs = 0;
        int totalMethodsAll = 0;
        int totalPassed = 0;
        int totalFailed = 0;

        if (classNames.isEmpty()) {
            printer.accept(INFO + " No test classes executed.");
        } else {
            // Calculate column widths
            int maxClassWidth = 40;
            for (String className : classNames) {
                if (className.length() > maxClassWidth) {
                    maxClassWidth = Math.min(className.length(), 50);
                }
            }

            // Print table header with proper separator
            printSeparatorLine(maxClassWidth);
            printTableRow(maxClassWidth, "Class", "Args", "Methods", "Passed", "Failed", "Status");
            printSeparatorLine(maxClassWidth);

            // Print data rows
            for (String className : classNames) {
                int argCount = summary.getClassArgumentCount(className);
                int methodCount = summary.getClassMethodCount(className);
                ExecutionSummary.ClassStats stats = classStatsMap.get(className);
                int passed = stats != null ? stats.passed.get() : 0;
                int failed = stats != null ? stats.failed.get() : 0;
                String status = failed > 0 ? "X" : "OK";

                // Truncate class name if too long
                String displayName = className;
                if (displayName.length() > maxClassWidth) {
                    displayName = displayName.substring(0, maxClassWidth - 3) + "...";
                }

                printTableRow(
                        maxClassWidth,
                        displayName,
                        String.valueOf(argCount),
                        String.valueOf(methodCount),
                        String.valueOf(passed),
                        String.valueOf(failed),
                        status);

                totalArgs += argCount;
                totalMethodsAll += methodCount;
                totalPassed += passed;
                totalFailed += failed;
            }

            // Print total row
            printSeparatorLine(maxClassWidth);
            printTableRow(
                    maxClassWidth,
                    "TOTAL",
                    String.valueOf(totalArgs),
                    String.valueOf(totalMethodsAll),
                    String.valueOf(totalPassed),
                    String.valueOf(totalFailed),
                    totalFailed > 0 ? "X" : "OK");
            printSeparatorLine(maxClassWidth);
        }

        printer.accept(INFO);

        // Print final result
        int grandTotal = totalPassed + totalFailed;
        if (totalFailed == 0) {
            printer.accept(INFO + "\033[1;32m" + " TESTS PASSED (" + grandTotal + "/" + grandTotal + ")" + "\033[0m");
        } else {
            printer.accept(
                    INFO + "\033[1;31m" + " TESTS FAILED (" + totalPassed + "/" + grandTotal + " passed)" + "\033[0m");
        }
    }

    /**
     * Prints a formatted line using {@link String#format(String, Object...)}.
     *
     * @param format the format string; never {@code null}
     * @param args format arguments; may be empty
     * @since 0.0.1
     */
    private void printLine(final @NonNull String format, final Object... args) {
        printer.accept(String.format(format, args));
    }

    /**
     * Prints a table separator line sized for the class-name column width.
     *
     * @param classWidth the class column width in characters; must be {@code >= 0}
     * @since 0.0.1
     */
    private void printSeparatorLine(final int classWidth) {
        StringBuilder sep = new StringBuilder();
        sep.append("+");
        // Class column: width + 2 (for spaces)
        for (int i = 0; i < classWidth + 2; i++) {
            sep.append("-");
        }
        sep.append("+");
        // Args column: 4 + 2 = 6
        for (int i = 0; i < 6; i++) {
            sep.append("-");
        }
        sep.append("+");
        // Methods column: 7 + 2 = 9
        for (int i = 0; i < 9; i++) {
            sep.append("-");
        }
        sep.append("+");
        // Passed column: 6 + 2 = 8
        for (int i = 0; i < 8; i++) {
            sep.append("-");
        }
        sep.append("+");
        // Failed column: 6 + 2 = 8
        for (int i = 0; i < 8; i++) {
            sep.append("-");
        }
        sep.append("+");
        // Status column: 6 + 2 = 8
        for (int i = 0; i < 8; i++) {
            sep.append("-");
        }
        sep.append("+");
        printLine(INFO + " " + sep);
    }

    /**
     * Performs printTableRow.
     *
     * @param classWidth the classWidth
     * @param className the className
     * @param args the args
     * @param methods the methods
     * @param passed the passed
     * @param failed the failed
     * @param status the status
     * @since 0.0.1
     */
    private void printTableRow(
            final int classWidth,
            final String className,
            final String args,
            final String methods,
            final String passed,
            final String failed,
            final String status) {
        StringBuilder row = new StringBuilder();
        row.append("| ");

        // Class column - left aligned, padded to classWidth
        row.append(className);
        for (int i = className.length(); i < classWidth; i++) {
            row.append(' ');
        }

        // Fixed-width columns with proper padding
        // Args: 4 chars + 2 spaces = 6 total
        row.append(" | ").append(String.format("%4s", args));
        // Methods: 7 chars + 2 spaces = 9 total
        row.append(" | ").append(String.format("%7s", methods));
        // Passed: 6 chars + 2 spaces = 8 total
        row.append(" | ").append(String.format("%6s", passed));
        // Failed: 6 chars + 2 spaces = 8 total
        row.append(" | ").append(String.format("%6s", failed));
        // Status: 6 chars + 2 spaces = 8 total
        row.append(" | ").append(String.format("%6s", status));
        row.append(" |");

        printLine(INFO + " " + row);
    }
}
