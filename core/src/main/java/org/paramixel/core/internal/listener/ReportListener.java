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

package org.paramixel.core.internal.listener;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Version;
import org.paramixel.core.support.ElapsedTimeFormatter;

/**
 * Writes a plain-text end-of-run summary report to a configured file.
 *
 * <p>Report output does not include the {@code [PARAMIXEL]} prefix since the destination is a dedicated file rather
 * than the console.
 */
public class ReportListener extends AbstractReportFileListener {

    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a report listener for the supplied file.
     *
     * @param reportFile the file that will contain the generated report
     */
    public ReportListener(final String reportFile) {
        super(reportFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeReport(Writer writer, Runner runner, Result result) throws IOException {
        var printWriter = new PrintWriter(writer, true);

        printWriter.println();
        var version = Version.getVersion();
        printWriter.println("Paramixel v" + version);

        new TreeSummaryRenderer(printWriter, false, false).renderSummary(runner, result);

        long runDurationMillis = result.getRunDuration().toMillis();
        String runDurationString = ElapsedTimeFormatter.formatElapsedTime(runDurationMillis);

        printWriter.println();
        printWriter.println("Paramixel v" + version);
        printWriter.println("Status      : " + Listeners.formatStatus(result.getStatus()));
        printWriter.println("Finished at : " + LocalDateTime.now().format(REPORT_TIMESTAMP_FORMAT));
        printWriter.println("Total time  : " + runDurationString);
        printWriter.println();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String formatName() {
        return "";
    }
}
