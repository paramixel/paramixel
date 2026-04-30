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

package org.paramixel.core.listener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.paramixel.core.Action;
import org.paramixel.core.Information;
import org.paramixel.core.Listener;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;
import org.paramixel.core.support.AnsiColor;

/**
 * A listener that generates an execution summary.
 *
 * <p>SummaryListener produces a formatted summary of action execution after the run
 * completes. It delegates to a {@link SummaryRenderer} to determine the visual format
 * (table, tree, etc.).</p>
 *
 * @see SummaryRenderer
 * @see TableSummaryRenderer
 * @see TreeSummaryRenderer
 * @see Listener
 */
public class SummaryListener implements Listener {

    private static final String PARAMIXEL = Listeners.PARAMIXEL;

    private final SummaryRenderer summaryRenderer;

    SummaryListener(SummaryRenderer summaryRenderer) {
        this.summaryRenderer = summaryRenderer;
    }

    private AnsiColor colorForStatus(Status status) {
        if (status.isPass()) {
            return AnsiColor.BOLD_GREEN_TEXT;
        } else if (status.isFailure()) {
            return AnsiColor.BOLD_RED_TEXT;
        } else {
            return AnsiColor.BOLD_ORANGE_TEXT;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation prints a start banner with the current Paramixel version.</p>
     */
    @Override
    public void runStarted(Runner runner, Action action) {
        System.out.println(PARAMIXEL + "Paramixel v" + Information.getVersion() + " starting...");
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation renders the configured summary view and prints an execution
     * footer containing aggregate status and timing information.</p>
     */
    @Override
    public void runCompleted(Runner runner, Action action) {
        System.out.println(PARAMIXEL);
        System.out.println(PARAMIXEL + "Paramixel v" + Information.getVersion());
        // System.out.println(PARAMIXEL + action.getName() + " completed");

        summaryRenderer.renderSummary(runner, action);

        long elapsedMillis = action.getResult().getElapsedTime().toMillis();
        String elapsedTimeString = formatElapsedTime(elapsedMillis);

        System.out.println(PARAMIXEL);
        System.out.println(PARAMIXEL + "Paramixel v" + Information.getVersion());
        System.out.println(PARAMIXEL + "Status      : "
                + colorForStatus(action.getResult().getStatus())
                        .format(action.getResult().getStatus().getDisplayName()));
        System.out.println(PARAMIXEL + "Finished at : "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println(PARAMIXEL + "Total time  : " + elapsedTimeString);
        System.out.println(PARAMIXEL);
    }

    /**
     * Formats elapsed time and conditionally appends raw milliseconds.
     */
    private String formatElapsedTime(long milliseconds) {
        String formatted = formatDuration(milliseconds);
        String rawMilliseconds = milliseconds + "ms";

        if (formatted.equals(rawMilliseconds)) {
            return formatted;
        }

        return formatted + " (" + rawMilliseconds + ")";
    }

    /**
     * Converts milliseconds into a human-readable string.
     */
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
