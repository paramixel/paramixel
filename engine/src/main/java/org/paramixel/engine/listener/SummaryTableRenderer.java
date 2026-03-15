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
import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;
import org.paramixel.engine.util.DurationUtils;
import org.paramixel.engine.util.SummaryClassNameUtil;

/**
 * Renders a summary table for test execution results.
 *
 * <p>This class handles all table rendering operations including:
 * <ul>
 *   <li>Column width calculation</li>
 *   <li>Header and separator generation</li>
 *   <li>Row formatting</li>
 *   <li>Table title rendering</li>
 * </ul>
 *
 * <p><b>Thread safety</b>
 * <p>This type is stateless. It uses the provided printer for output.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class SummaryTableRenderer {

    /**
     * Column headers for the summary table.
     */
    private static final String[] TABLE_HEADERS =
            new String[] {"Class", "Args", "Methods", "Passed", "Failed", "Status", "Time"};

    /**
     * Printer used for emitting the execution report.
     */
    private final Consumer<String> printer;

    /**
     * INFO label prefix.
     */
    private static final String INFO = "[" + "\033[1;34m" + "INFO" + "\033[0m" + "]";

    /**
     * Maximum rendered class-name length for the summary table class column.
     */
    private final int summaryClassNameMaxLength;

    /**
     * Creates a new renderer instance.
     *
     * @param printer the printer to receive report lines; never {@code null}
     * @param summaryClassNameMaxLength the maximum rendered class-name length
     */
    public SummaryTableRenderer(final @NonNull Consumer<String> printer, final int summaryClassNameMaxLength) {
        this.printer = Objects.requireNonNull(printer, "printer must not be null");
        this.summaryClassNameMaxLength = summaryClassNameMaxLength;
    }

    /**
     * Renders the summary table.
     *
     * @param classNames sorted list of class names to render
     * @param summary the execution summary containing class statistics
     * @param duration the total execution duration string
     */
    public void renderTable(
            final @NonNull List<String> classNames,
            final AbstractEngineExecutionListener.@NonNull ExecutionSummary summary,
            final @NonNull String duration) {
        if (classNames.isEmpty()) {
            printer.accept(INFO + " No test classes executed.");
            return;
        }

        ColumnWidths widths = new ColumnWidths(TABLE_HEADERS);
        List<TableRow> rows = new ArrayList<>(classNames.size());

        // Build data rows
        java.util.concurrent.ConcurrentHashMap<String, AbstractEngineExecutionListener.ExecutionSummary.ClassStats>
                classStatsMap = summary.getClassStatsMap();
        for (String className : classNames) {
            final String renderedClassName = SummaryClassNameUtil.abbreviateClassName(
                    "paramixel.summary.classNameMaxLength", className, summaryClassNameMaxLength);
            int argCount = summary.getClassArgumentCount(className);
            int methodCount = summary.getClassMethodCount(className);
            AbstractEngineExecutionListener.ExecutionSummary.ClassStats stats = classStatsMap.get(className);
            int passed = stats != null ? stats.passed.get() : 0;
            int failed = stats != null ? stats.failed.get() : 0;
            String status = failed > 0 ? "X" : "OK";
            long classDuration = stats != null ? stats.getTotalDurationMillis() : 0L;
            String classDurationString = DurationUtils.formatMillis(classDuration);

            TableRow row = new TableRow(
                    renderedClassName,
                    String.valueOf(argCount),
                    String.valueOf(methodCount),
                    String.valueOf(passed),
                    String.valueOf(failed),
                    status,
                    classDurationString);
            rows.add(row);
            widths.observeRow(row);
        }

        // Build totals row (time column blank)
        int totalArgs = classNames.stream()
                .mapToInt(name -> summary.getClassArgumentCount(name))
                .sum();
        int totalMethodsAll = classNames.stream()
                .mapToInt(name -> summary.getClassMethodCount(name))
                .sum();
        int totalPassed = classStatsMap.values().stream()
                .mapToInt(stats -> stats.passed.get())
                .sum();
        int totalFailed = classStatsMap.values().stream()
                .mapToInt(stats -> stats.failed.get())
                .sum();

        TableRow totalRow = new TableRow(
                "TOTAL",
                String.valueOf(totalArgs),
                String.valueOf(totalMethodsAll),
                String.valueOf(totalPassed),
                String.valueOf(totalFailed),
                totalFailed > 0 ? "X" : "OK",
                "");
        widths.observeRow(totalRow);

        // Build column configurations
        ColumnConfig[] columns = new ColumnConfig[] {
            new ColumnConfig(TABLE_HEADERS[0], widths.classWidth, false),
            new ColumnConfig(TABLE_HEADERS[1], widths.argsWidth, true),
            new ColumnConfig(TABLE_HEADERS[2], widths.methodsWidth, true),
            new ColumnConfig(TABLE_HEADERS[3], widths.passedWidth, true),
            new ColumnConfig(TABLE_HEADERS[4], widths.failedWidth, true),
            new ColumnConfig(TABLE_HEADERS[5], widths.statusWidth, false),
            new ColumnConfig(TABLE_HEADERS[6], widths.timeWidth, true)
        };

        String separator = buildSeparatorLine(columns);
        printer.accept(INFO + " " + separator);
        printTableTitleRow(separator.length(), "Paramixel Test Summary", "");
        printer.accept(INFO + " " + separator);
        printer.accept(INFO + " " + buildHeaderRow(columns));
        printer.accept(INFO + " " + separator);

        // Print data rows
        for (TableRow row : rows) {
            printer.accept(INFO + " " + buildDataRow(columns, row));
        }

        printer.accept(INFO + " " + separator);
        printer.accept(INFO + " " + buildDataRow(columns, totalRow));
        printer.accept(INFO + " " + separator);
    }

    /**
     * Prints a title row that spans all columns with left-aligned title.
     *
     * @param tableWidth the width of the full table line in characters
     * @param title the title text
     * @param duration the duration string (empty to omit)
     */
    private void printTableTitleRow(final int tableWidth, final @NonNull String title, final @NonNull String duration) {
        String content = duration.isEmpty() ? title : title + " | " + duration;
        int contentWidth = tableWidth - 4;
        int padding = contentWidth - content.length();

        StringBuilder row = new StringBuilder();
        row.append("| ").append(content);
        row.append(" ".repeat(Math.max(0, padding)));
        row.append(" |");

        printer.accept(INFO + " " + row);
    }

    /**
     * Builds a separator line for the given columns.
     *
     * @param columns the column configurations
     * @return the separator line
     */
    private String buildSeparatorLine(final @NonNull ColumnConfig[] columns) {
        Objects.requireNonNull(columns, "columns must not be null");
        StringBuilder sep = new StringBuilder();
        sep.append('+');
        for (ColumnConfig column : columns) {
            sep.append("-".repeat(Math.max(0, column.width + 2)));
            sep.append('+');
        }
        return sep.toString();
    }

    /**
     * Builds a header row for the given columns.
     *
     * @param columns the column configurations
     * @return the header row
     */
    private String buildHeaderRow(final @NonNull ColumnConfig[] columns) {
        Objects.requireNonNull(columns, "columns must not be null");
        StringBuilder row = new StringBuilder();
        row.append('|');
        for (ColumnConfig column : columns) {
            row.append(' ');
            row.append(pad(column.header, column.width, column.rightAligned));
            row.append(' ');
            row.append('|');
        }
        return row.toString();
    }

    /**
     * Builds a data row for the given columns.
     *
     * @param columns the column configurations
     * @param row the row values
     * @return the rendered data row
     */
    private String buildDataRow(final @NonNull ColumnConfig[] columns, final @NonNull TableRow row) {
        Objects.requireNonNull(columns, "columns must not be null");
        Objects.requireNonNull(row, "row must not be null");

        String[] values =
                new String[] {row.className, row.args, row.methods, row.passed, row.failed, row.status, row.time};

        StringBuilder out = new StringBuilder();
        out.append('|');
        for (int i = 0; i < columns.length; i++) {
            ColumnConfig column = columns[i];
            out.append(' ');
            out.append(pad(values[i], column.width, column.rightAligned));
            out.append(' ');
            out.append('|');
        }
        return out.toString();
    }

    /**
     * Pads a value to the requested width.
     *
     * @param value the value
     * @param width the target width
     * @param rightAligned true to right-align, false to left-align
     * @return the padded value
     */
    private String pad(final @NonNull String value, final int width, final boolean rightAligned) {
        Objects.requireNonNull(value, "value must not be null");
        int padding = Math.max(0, width - value.length());
        if (padding == 0) {
            return value;
        }

        if (rightAligned) {
            return " ".repeat(padding) + value;
        }

        return value + " ".repeat(padding);
    }

    /**
     * Column width accumulator for the summary table.
     */
    private static final class ColumnWidths {

        /**
         * Width of the class column.
         */
        private int classWidth;

        /**
         * Width of the args column.
         */
        private int argsWidth;

        /**
         * Width of the methods column.
         */
        private int methodsWidth;

        /**
         * Width of the passed column.
         */
        private int passedWidth;

        /**
         * Width of the failed column.
         */
        private int failedWidth;

        /**
         * Width of the status column.
         */
        private int statusWidth;

        /**
         * Width of the time column.
         */
        private int timeWidth;

        /**
         * Creates a new column-width accumulator initialized from headers.
         *
         * @param headers the table headers
         */
        private ColumnWidths(final @NonNull String[] headers) {
            Objects.requireNonNull(headers, "headers must not be null");
            this.classWidth = headers[0].length();
            this.argsWidth = headers[1].length();
            this.methodsWidth = headers[2].length();
            this.passedWidth = headers[3].length();
            this.failedWidth = headers[4].length();
            this.statusWidth = headers[5].length();
            this.timeWidth = headers[6].length();
        }

        /**
         * Observes a row and updates all tracked column widths.
         *
         * @param row the row to observe
         */
        private void observeRow(final @NonNull TableRow row) {
            Objects.requireNonNull(row, "row must not be null");
            this.classWidth = Math.max(this.classWidth, row.className.length());
            this.argsWidth = Math.max(this.argsWidth, row.args.length());
            this.methodsWidth = Math.max(this.methodsWidth, row.methods.length());
            this.passedWidth = Math.max(this.passedWidth, row.passed.length());
            this.failedWidth = Math.max(this.failedWidth, row.failed.length());
            this.statusWidth = Math.max(this.statusWidth, row.status.length());
            if (!"TOTAL".equals(row.className)) {
                this.timeWidth = Math.max(this.timeWidth, row.time.length());
            }
        }
    }

    /**
     * A single row of data for the summary table.
     */
    private static final class TableRow {

        /**
         * Value for the class column.
         */
        private final String className;

        /**
         * Value for the args column.
         */
        private final String args;

        /**
         * Value for the methods column.
         */
        private final String methods;

        /**
         * Value for the passed column.
         */
        private final String passed;

        /**
         * Value for the failed column.
         */
        private final String failed;

        /**
         * Value for the status column.
         */
        private final String status;

        /**
         * Value for the time column.
         */
        private final String time;

        /**
         * Creates a new table row.
         *
         * @param className the class name
         * @param args the args count
         * @param methods the method count
         * @param passed the passed count
         * @param failed the failed count
         * @param status the status
         * @param time the time
         */
        private TableRow(
                final @NonNull String className,
                final @NonNull String args,
                final @NonNull String methods,
                final @NonNull String passed,
                final @NonNull String failed,
                final @NonNull String status,
                final @NonNull String time) {
            this.className = Objects.requireNonNull(className, "className must not be null");
            this.args = Objects.requireNonNull(args, "args must not be null");
            this.methods = Objects.requireNonNull(methods, "methods must not be null");
            this.passed = Objects.requireNonNull(passed, "passed must not be null");
            this.failed = Objects.requireNonNull(failed, "failed must not be null");
            this.status = Objects.requireNonNull(status, "status must not be null");
            this.time = Objects.requireNonNull(time, "time must not be null");
        }
    }

    /**
     * Column configuration for table rendering.
     */
    private static final class ColumnConfig {

        /**
         * Column header text.
         */
        private final String header;

        /**
         * Column width.
         */
        private final int width;

        /**
         * Whether values are right-aligned.
         */
        private final boolean rightAligned;

        /**
         * Creates a new column config.
         *
         * @param header the header
         * @param width the width
         * @param rightAligned true for right alignment
         */
        private ColumnConfig(final @NonNull String header, final int width, final boolean rightAligned) {
            this.header = Objects.requireNonNull(header, "header must not be null");
            this.width = width;
            this.rightAligned = rightAligned;
        }
    }
}
