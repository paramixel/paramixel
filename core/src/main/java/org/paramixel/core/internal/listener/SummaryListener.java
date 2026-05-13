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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;
import org.paramixel.core.Version;
import org.paramixel.core.support.ElapsedTimeFormatter;

/**
 * Prints a run-level summary to the console after execution completes.
 *
 * <p>The summary output is delegated to a {@link SummaryRenderer}, followed by common footer information such as the
 * final status and total run time.
 */
public class SummaryListener implements Listener {

    private final SummaryRenderer summaryRenderer;

    private final PrintWriter out;

    private final boolean ansiEnabled;

    /**
     * Creates a summary listener that uses the supplied renderer.
     *
     * @param summaryRenderer the renderer used to print the action summary
     */
    public SummaryListener(final SummaryRenderer summaryRenderer) {
        this.summaryRenderer = Objects.requireNonNull(summaryRenderer, "summaryRenderer must not be null");
        this.out = null;
        this.ansiEnabled = true;
    }

    /**
     * Creates a summary listener that uses the supplied renderer and output destination.
     *
     * @param summaryRenderer the renderer used to print the action summary
     * @param out the output writer used for summary lines
     * @param ansiEnabled whether ANSI formatting should be used
     */
    public SummaryListener(final SummaryRenderer summaryRenderer, final PrintWriter out, final boolean ansiEnabled) {
        this.summaryRenderer = Objects.requireNonNull(summaryRenderer, "summaryRenderer must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.ansiEnabled = ansiEnabled;
    }

    /**
     * Creates a summary listener that uses the supplied renderer and output destination.
     *
     * @param summaryRenderer the renderer used to print the action summary
     * @param out the output stream used for summary lines
     * @param ansiEnabled whether ANSI formatting should be used
     */
    public SummaryListener(final SummaryRenderer summaryRenderer, final PrintStream out, final boolean ansiEnabled) {
        this.summaryRenderer = Objects.requireNonNull(summaryRenderer, "summaryRenderer must not be null");
        this.out = new PrintWriter(Objects.requireNonNull(out, "out must not be null"), true);
        this.ansiEnabled = ansiEnabled;
    }

    private PrintWriter getWriter() {
        return out == null ? new PrintWriter(System.out, true) : out;
    }

    private String formatStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        return ansiEnabled ? Listeners.formatAnsiStatus(status) : Listeners.formatStatus(status);
    }

    private String getPrefix() {
        return ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
    }

    @Override
    public void runStarted(Runner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        var writer = getWriter();
        var prefix = getPrefix();
        var version = Version.getVersion();
        writer.println(prefix + "Paramixel v" + version + " starting...");
    }

    @Override
    public void runCompleted(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");
        var writer = getWriter();
        var prefix = getPrefix();
        var version = Version.getVersion();
        String versionLine = "Paramixel v" + version;
        String statusLine = "Status      : " + Listeners.formatStatus(result.getStatus());
        String finishedLine =
                "Finished at : " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String timeLine = "Total time  : "
                + ElapsedTimeFormatter.formatElapsedTime(result.getRunDuration().toMillis());

        int maxLineLength = Math.max(
                Math.max(versionLine.length(), statusLine.length()),
                Math.max(finishedLine.length(), timeLine.length()));
        String dashes = "-".repeat(maxLineLength);

        writer.println(prefix + dashes);
        writer.println(prefix + versionLine);

        summaryRenderer.renderSummary(runner, result);

        String runDurationString =
                ElapsedTimeFormatter.formatElapsedTime(result.getRunDuration().toMillis());

        writer.println(prefix + dashes);
        writer.println(prefix + versionLine);
        writer.println(prefix + dashes);
        writer.println(prefix + "Status      : " + formatStatus(result.getStatus()));
        writer.println(prefix + "Finished at : "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        writer.println(prefix + "Total time  : " + runDurationString);
        writer.println(prefix + dashes);
    }
}
