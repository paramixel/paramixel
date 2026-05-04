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

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;
import org.paramixel.core.Version;
import org.paramixel.core.support.AnsiColor;

/**
 * Prints a run-level summary to the console after execution completes.
 *
 * <p>The summary output is delegated to a {@link SummaryRenderer}, followed by common footer information such as the
 * final status and total run time.
 */
public class SummaryListener implements Listener {

    private final SummaryRenderer summaryRenderer;

    private final PrintStream out;

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
     * @param out the output stream used for summary lines
     * @param ansiEnabled whether ANSI formatting should be used
     */
    public SummaryListener(final SummaryRenderer summaryRenderer, final PrintStream out, final boolean ansiEnabled) {
        this.summaryRenderer = Objects.requireNonNull(summaryRenderer, "summaryRenderer must not be null");
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.ansiEnabled = ansiEnabled;
    }

    private PrintStream out() {
        return out == null ? System.out : out;
    }

    private String formatStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        if (!ansiEnabled) {
            return resultDisplayName(status);
        }
        if (status.isPass()) {
            return AnsiColor.BOLD_GREEN_TEXT.format(status.getDisplayName());
        } else if (status.isFailure()) {
            return AnsiColor.BOLD_RED_TEXT.format(status.getDisplayName());
        } else {
            return AnsiColor.BOLD_ORANGE_TEXT.format(status.getDisplayName());
        }
    }

    private static String resultDisplayName(Status status) {
        return status.getDisplayName();
    }

    private String prefix() {
        return ansiEnabled ? Constants.PARAMIXEL : Constants.PARAMIXEL_PLAIN;
    }

    @Override
    public void runStarted(Runner runner) {
        Objects.requireNonNull(runner, "runner must not be null");
        out().println(prefix() + "Paramixel v" + Version.getVersion() + " starting...");
    }

    @Override
    public void runCompleted(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");
        out().println(prefix());
        out().println(prefix() + "Paramixel v" + Version.getVersion());

        summaryRenderer.renderSummary(runner, result);

        long elapsedMillis = result.getElapsedTime().toMillis();
        String elapsedTimeString = formatElapsedTime(elapsedMillis);

        out().println(prefix());
        out().println(prefix() + "Paramixel v" + Version.getVersion());
        out().println(prefix() + "Status      : " + formatStatus(result.getStatus()));
        out().println(prefix() + "Finished at : "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        out().println(prefix() + "Total time  : " + elapsedTimeString);
        out().println(prefix());
    }

    private String formatElapsedTime(long milliseconds) {
        String formatted = formatDuration(milliseconds);
        String rawMilliseconds = milliseconds + "ms";

        if (formatted.equals(rawMilliseconds)) {
            return formatted;
        }

        return formatted + " (" + rawMilliseconds + ")";
    }

    private String formatDuration(long milliseconds) {
        long hours = milliseconds / 3_600_000;
        milliseconds %= 3_600_000;

        long minutes = milliseconds / 60_000;
        milliseconds %= 60_000;

        long seconds = milliseconds / 1_000;
        milliseconds %= 1_000;

        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(hours).append("h ");
        }

        if (minutes > 0 || hours > 0) {
            result.append(minutes).append("m ");
        }

        if (seconds > 0 || minutes > 0 || hours > 0) {
            result.append(seconds).append("s ");
        }

        result.append(milliseconds).append("ms");

        return result.toString().trim();
    }
}
