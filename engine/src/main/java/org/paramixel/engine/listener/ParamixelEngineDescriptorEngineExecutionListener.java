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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;

/**
 * Execution listener for the engine root descriptor.
 */
public class ParamixelEngineDescriptorEngineExecutionListener extends AbstractEngineExecutionListener {

    private long startTimeMillis;

    @Override
    public void executionStarted(final @NonNull TestDescriptor testDescriptor) {
        startTimeMillis = System.currentTimeMillis();
        resetExecutionSummary();
        System.out.println(INFO + " Paramixel starting...");
    }

    @Override
    public void executionFinished(
            final @NonNull TestDescriptor testDescriptor, final @NonNull TestExecutionResult testExecutionResult) {
        long durationMillis = System.currentTimeMillis() - startTimeMillis;
        String duration = formatDuration(durationMillis);

        ExecutionSummary summary = getExecutionSummary();

        // Print header
        printLine(INFO + " ╔════════════════════════════════════════════════════════════════════════╗");
        printLine(INFO + " ║                    PARAMIXEL TEST EXECUTION REPORT                     ║");
        printLine(INFO + " ╠════════════════════════════════════════════════════════════════════════╣");
        String durationContent = " Duration: " + duration;
        int contentWidth = 72;
        int padding = contentWidth - durationContent.length();
        StringBuilder durationLine = new StringBuilder();
        durationLine.append("║");
        durationLine.append(durationContent);
        for (int i = 0; i < padding; i++) {
            durationLine.append(" ");
        }
        durationLine.append("║");
        printLine(INFO + " " + durationLine.toString());
        printLine(INFO + " ╚════════════════════════════════════════════════════════════════════════╝");
        System.out.println(INFO);

        // Print hierarchical summary
        int totalClasses = summary.getTestClassPassed() + summary.getTestClassFailed() + summary.getTestClassSkipped();
        int totalArguments =
                summary.getTestArgumentPassed() + summary.getTestArgumentFailed() + summary.getTestArgumentSkipped();
        int totalMethods =
                summary.getTestMethodPassed() + summary.getTestMethodFailed() + summary.getTestMethodSkipped();

        System.out.println(INFO + " " + totalClasses + " Classes tested");
        System.out.println(INFO + " └── " + totalArguments + " Arguments tested");
        System.out.println(INFO + "     └── " + totalMethods + " Test methods tested");
        System.out.println(INFO);

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
            System.out.println(INFO + " No test classes executed.");
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

        System.out.println(INFO);

        // Print final result
        int grandTotal = totalPassed + totalFailed;
        if (totalFailed == 0) {
            System.out.println(
                    INFO + "\033[1;32m" + " TESTS PASSED (" + grandTotal + "/" + grandTotal + ")" + "\033[0m");
        } else {
            System.out.println(
                    INFO + "\033[1;31m" + " TESTS FAILED (" + totalPassed + "/" + grandTotal + " passed)" + "\033[0m");
        }
    }

    private void printLine(String format, Object... args) {
        System.out.println(String.format(format, args));
    }

    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.3fs", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            long remainingMillis = millis % 1000;
            return String.format("%dm %ds %dms", minutes, seconds, remainingMillis);
        }
    }

    private void printSeparatorLine(int classWidth) {
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
        printLine(INFO + " " + sep.toString());
    }

    private void printTableRow(
            int classWidth,
            String className,
            String args,
            String methods,
            String passed,
            String failed,
            String status) {
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
