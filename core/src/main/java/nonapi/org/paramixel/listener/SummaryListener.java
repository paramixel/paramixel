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

package nonapi.org.paramixel.listener;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import nonapi.org.paramixel.ConcreteResult;
import nonapi.org.paramixel.support.AnsiDetector;
import nonapi.org.paramixel.support.ElapsedTimeFormatter;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Version;

/**
 * Prints a descriptor tree summary when a run completes, preceded by a starting banner and
 * followed by a footer with status, timestamp, and total elapsed time.
 *
 * <p>The action tree rendering is delegated to a {@link SummaryRenderer}. Each rendered line is
 * prefixed with the {@code [PARAMIXEL]} tag (ANSI-colored or plain depending on configuration).
 *
 * <p><strong>Thread safety:</strong> the renderer and the exclude set are published by
 * {@link #onRunStarted(Configuration)} before any execution callback, and the tree is rendered once on
 * {@link #onRunCompleted(Result)}. Output is written to {@code System.out} at the moment of writing, so a
 * caller that redirects {@code System.out} before a run sees the whole output.
 */
public final class SummaryListener implements Listener {

    private volatile SummaryRenderer renderer;
    private volatile boolean ansiEnabled;
    private volatile EnumSet<ExcludeTarget> excludes = EnumSet.noneOf(ExcludeTarget.class);

    /**
     * Creates a summary listener.
     */
    public SummaryListener() {}

    @Override
    public void onRunStarted(final Configuration configuration) {
        if (renderer == null) {
            var value = configuration.getString(Configuration.ANSI).orElse(null);
            if (value == null || value.isBlank()) {
                this.ansiEnabled = AnsiDetector.isAnsiAvailable();
            } else {
                this.ansiEnabled = "true".equals(value.strip().toLowerCase(Locale.ROOT));
            }
            this.renderer = new TreeSummaryRenderer(ansiEnabled);
            this.excludes = Listeners.parseExcludes(
                    configuration.getString(Configuration.LISTENER_EXCLUDE).orElse(null));
        }
        printBanner();
    }

    /**
     * Prints the version banner and starting message to the output writer.
     */
    private void printBanner() {
        if (excludes.contains(ExcludeTarget.SUMMARY_HEADER)) {
            return;
        }
        var writer = getWriter();
        var prefix = getPrefix();
        var version = Version.version();
        writer.println(prefix + "Paramixel v" + version + " starting...");
    }

    /**
     * Delegates to the {@link SummaryRenderer} for the action tree, then prints a footer with
     * the final status, timestamp, and total elapsed time.
     *
     * <p>When the result contains no descriptor, prints a "no tests found" message and skips the footer.
     */
    @Override
    public void onRunCompleted(final Result result) {
        var excludeTree = excludes.contains(ExcludeTarget.SUMMARY_TREE);
        var excludeFooter = excludes.contains(ExcludeTarget.SUMMARY_FOOTER);

        var writer = getWriter();
        var prefix = getPrefix();

        var optionalRoot = result.descriptor();
        if (optionalRoot.isEmpty()) {
            writer.println(prefix + "No Paramixel tests found");
            return;
        }

        if (excludeTree && excludeFooter) {
            return;
        }

        var root = optionalRoot.get();

        var version = Version.version();
        var versionLine = "Paramixel v" + version;

        if (!excludeTree && !excludeFooter) {
            writer.println(prefix + "-".repeat(versionLine.length()));
            writer.println(prefix + versionLine);
        }

        if (!excludeTree) {
            var output = renderer.render(root);
            for (var line : output.split(System.lineSeparator())) {
                if (!line.isBlank()) {
                    writer.println(prefix + line);
                }
            }
        }

        if (!excludeFooter) {
            var finishedAtFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            var statusLine = "Status      : " + Listeners.formatStatus(result);
            var finishedLine = "Finished at : " + finishedAtFormatted;
            var timeLine = "Total time  : " + ElapsedTimeFormatter.formatElapsedTime(Listeners.elapsedMillis(root));
            var poolLine = poolLine(result);

            int maxLineLength = Math.max(
                    Math.max(versionLine.length(), statusLine.length()),
                    Math.max(finishedLine.length(), Math.max(timeLine.length(), poolLine.length())));
            var dashes = "-".repeat(maxLineLength);

            writer.println(prefix + dashes);
            writer.println(prefix + versionLine);
            writer.println(prefix + dashes);
            writer.println(prefix + "Status      : " + formatStatus(result));
            writer.println(prefix + "Finished at : " + finishedAtFormatted);
            writer.println(
                    prefix + "Total time  : " + ElapsedTimeFormatter.formatElapsedTime(Listeners.elapsedMillis(root)));
            if (!poolLine.isEmpty()) {
                writer.println(prefix + poolLine);
            }
            writer.println(prefix + dashes);
        }
    }

    /**
     * Formats the pool line: the size of every pool the run used and its peak usage.
     *
     * <p>A peak that equals the size means the pool was saturated; a peak of one means the work serialized. Both are
     * the usual reason a parallel run was slower than expected.
     *
     * @param result the run result; must not be {@code null}
     * @return the line, or an empty string when no pool was used
     */
    private static String poolLine(final Result result) {
        if (!(result instanceof ConcreteResult concrete) || concrete.poolStats().isEmpty()) {
            return "";
        }
        var parts = new java.util.ArrayList<String>();
        for (var stats : concrete.poolStats()) {
            parts.add(stats.name() + " " + stats.peakInUse() + "/" + stats.size());
        }
        return "Pools       : " + String.join(", ", parts);
    }

    private String getPrefix() {
        return ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
    }

    /**
     * Returns a writer for the current {@code System.out}.
     *
     * <p>A summary is printed in three callbacks per run, so a writer is created per callback rather than cached;
     * that keeps the output bound to the stream that is current when the text is written.
     *
     * @return a writer for {@code System.out}; never {@code null}
     */
    private PrintWriter getWriter() {
        return new PrintWriter(System.out, true);
    }

    private String formatStatus(final Result result) {
        return ansiEnabled ? Listeners.formatAnsiStatus(result) : Listeners.formatStatus(result);
    }
}
