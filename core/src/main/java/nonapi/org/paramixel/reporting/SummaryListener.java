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

package nonapi.org.paramixel.reporting;

import java.io.PrintStream;
import java.util.Optional;
import nonapi.org.paramixel.support.AnsiColor;
import nonapi.org.paramixel.support.ElapsedTimeFormatter;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;

/**
 * Prints the result tree, the failure detail, and the run totals after a run completes.
 *
 * <p>The summary reads only the immutable {@link Result}, so console output, a report file, and any integration cannot
 * disagree about what happened. Counters count occurrences once, no matter how many attempts or phases they had.
 */
public final class SummaryListener implements Listener {

    private final ConsoleSupport console;
    private final PrintStream out;

    /**
     * Creates a listener.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @param out the output stream; must not be {@code null}
     */
    public SummaryListener(final Configuration configuration, final PrintStream out) {
        this.console = new ConsoleSupport(configuration);
        this.out = out;
    }

    @Override
    public void onRunCompleted(final Result result) {
        if (console.enabled("summary.header")) {
            out.println();
            out.println(console.line(console.color(AnsiColor.BOLD_WHITE_TEXT, "Summary")));
        }
        result.root().ifPresent(root -> {
            if (console.enabled("summary.tree")) {
                TreeRenderer.render(root, console).forEach(line -> out.println(console.line(line)));
            }
            for (String line : TreeRenderer.renderFailures(root)) {
                out.println(console.line(line));
            }
        });
        for (org.paramixel.api.Failure failure : result.failures()) {
            out.println(console.line(console.color(AnsiColor.BOLD_RED_TEXT, "infrastructure") + " [" + failure.phase()
                    + "] " + failure.message()));
            failure.throwable().ifPresent(throwable -> out.println(console.line("  " + throwable)));
        }
        for (String diagnostic : result.diagnostics()) {
            out.println(console.line(console.color(AnsiColor.BOLD_GRAY_TEXT, "diagnostic") + " " + diagnostic));
        }
        if (console.enabled("summary.footer")) {
            printTotals(result);
        }
    }

    private void printTotals(final Result result) {
        out.println();
        out.println(console.line("occurrences: " + result.occurrenceCount()
                + " passed: " + result.passedCount()
                + " failed: " + result.failedCount()
                + " skipped: " + result.skippedCount()
                + " aborted: " + result.abortedCount()
                + " canceled: " + result.canceledCount()
                + " timedOut: " + result.timedOutCount()
                + " attempts: " + result.attemptCount()));
        final var elapsed = elapsedMillis(result);
        out.println(console.line("result: " + console.inlineLabel(result.status()) + " exitCode: "
                + result.exitCode() + " elapsed: " + ElapsedTimeFormatter.formatDuration(elapsed)
                + (result.isQuiescent() ? "" : " non-quiescent")));
    }

    private static long elapsedMillis(final Result result) {
        final Optional<java.time.Instant> start = result.startedAt();
        final Optional<java.time.Instant> end = result.completedAt();
        if (start.isEmpty() || end.isEmpty()) {
            return 0L;
        }
        return java.time.Duration.between(start.orElseThrow(), end.orElseThrow())
                .toMillis();
    }
}
