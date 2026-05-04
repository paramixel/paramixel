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

package org.paramixel.core.spi.listener;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

/**
 * Writes a plain-text end-of-run summary report to a per-run file.
 */
public class ReportListener implements Listener {

    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path reportDirectory;

    /**
     * Creates a report listener for the supplied directory.
     *
     * @param reportDirectory the directory that will contain generated report files
     */
    public ReportListener(final String reportDirectory) {
        Objects.requireNonNull(reportDirectory, "reportDirectory must not be null");
        this.reportDirectory = Path.of(reportDirectory);
    }

    @Override
    public void runCompleted(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");

        try {
            Files.createDirectories(reportDirectory);
            Path reportFile =
                    reportDirectory.resolve("paramixel_" + FILE_TIMESTAMP_FORMAT.format(LocalDateTime.now()) + ".log");

            try (PrintStream printStream = new PrintStream(Files.newOutputStream(reportFile))) {
                SummaryListener summaryListener =
                        new SummaryListener(new TreeSummaryRenderer(printStream, false), printStream, false);
                summaryListener.runCompleted(runner, result);
            }
        } catch (IOException e) {
            System.err.println(Constants.PARAMIXEL_PLAIN + "Unable to write report file: " + e.getMessage());
        }
    }
}
