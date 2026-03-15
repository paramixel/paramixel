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
 */
public final class ParamixelEngineDescriptorEngineExecutionListener extends AbstractEngineExecutionListener {

    /**
     * Printer used for emitting the execution report.
     */
    private final Consumer<String> printer;

    /**
     * Maximum rendered class-name length for the summary table class column.
     */
    private final int summaryClassNameMaxLength;

    /**
     * Start time for the current engine execution in epoch milliseconds.
     */
    private long startTimeMillis;

    /**
     * Column headers for the summary table.
     */
    private static final String[] TABLE_HEADERS =
            new String[] {"Class", "Args", "Methods", "Passed", "Failed", "Status", "Time"};

    /**
     * Creates a listener that writes the report to the provided printer.
     *
     * @param printer the printer to receive report lines; never {@code null}
     */
    public ParamixelEngineDescriptorEngineExecutionListener(final @NonNull Consumer<String> printer) {
        this(printer, Integer.MAX_VALUE);
    }

    /**
     * Creates a listener that writes the report to the provided printer.
     *
     * @param printer the printer to receive report lines; never {@code null}
     * @param summaryClassNameMaxLength the maximum rendered class-name length
     */
    public ParamixelEngineDescriptorEngineExecutionListener(
            final @NonNull Consumer<String> printer, final int summaryClassNameMaxLength) {
        this.printer = Objects.requireNonNull(printer, "printer must not be null");
        this.summaryClassNameMaxLength = summaryClassNameMaxLength;
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

        // Render summary table (delegated to SummaryTableRenderer)
        ConcurrentHashMap<String, ExecutionSummary.ClassStats> classStatsMap = summary.getClassStatsMap();
        List<String> classNames = new ArrayList<>(classStatsMap.keySet());
        classNames.sort(String::compareTo);

        final SummaryTableRenderer renderer = new SummaryTableRenderer(printer, summaryClassNameMaxLength);
        renderer.renderTable(classNames, summary, duration);

        printer.accept(INFO);

        // Print status and execution time
        int totalFailed = summary.getClassStatsMap().values().stream()
                .mapToInt(stats -> stats.failed.get())
                .sum();
        String resultText = totalFailed == 0 ? "TESTS PASSED" : "TESTS FAILED";
        String resultColor = totalFailed == 0 ? "\033[1;32m" : "\033[1;31m";

        // Align colons with space on each side: "Status" (6) vs "Execution Time" (14)
        // "Status" needs 9 trailing spaces to align colon at position 16
        // "Status         :" in white (default), resultText in green/red
        printer.accept(INFO + " Status         : " + resultColor + resultText + " \033[0m");
        printer.accept(INFO + " Execution Time : " + duration);
    }
}
