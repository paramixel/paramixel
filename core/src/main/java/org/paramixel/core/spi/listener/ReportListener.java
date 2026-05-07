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
import org.paramixel.core.Status;
import org.paramixel.core.Version;
import org.paramixel.core.support.Arguments;
import org.paramixel.core.support.ElapsedTimeFormatter;

/**
 * Writes a plain-text end-of-run summary report to a configured file.
 *
 * <p>Report output does not include the {@code [PARAMIXEL]} prefix since the destination is a dedicated file rather
 * than the console.
 */
public class ReportListener implements Listener {

    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path reportFile;

    /**
     * Creates a report listener for the supplied file.
     *
     * @param reportFile the file that will contain the generated report
     */
    public ReportListener(final String reportFile) {
        Objects.requireNonNull(reportFile, "reportFile must not be null");
        Arguments.requireNonBlank(reportFile, "reportFile must not be blank");
        this.reportFile = TildePathExpander.expand(reportFile);
    }

    @Override
    public void runCompleted(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");

        try {
            Path parent = reportFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (PrintStream printStream = new PrintStream(Files.newOutputStream(reportFile))) {
                printStream.println();
                printStream.println("Paramixel v" + Version.getVersion());

                new TreeSummaryRenderer(printStream, false, false).renderSummary(runner, result);

                long runDurationMillis = result.getRunDuration().toMillis();
                String runDurationString = ElapsedTimeFormatter.formatElapsedTime(runDurationMillis);

                printStream.println();
                printStream.println("Paramixel v" + Version.getVersion());
                printStream.println("Status      : " + formatStatus(result.getStatus()));
                printStream.println("Finished at : " + LocalDateTime.now().format(REPORT_TIMESTAMP_FORMAT));
                printStream.println("Total time  : " + runDurationString);
                printStream.println();
            }
        } catch (IOException e) {
            System.err.println(Constants.PARAMIXEL_PLAIN + "Unable to write report file: " + e.getMessage());
        }
    }

    private static String formatStatus(Status status) {
        if (status.isStaged()) {
            return "STAGED";
        } else if (status.isPass()) {
            return "PASS";
        } else if (status.isFailure()) {
            return "FAIL";
        } else {
            return "SKIP";
        }
    }
}
