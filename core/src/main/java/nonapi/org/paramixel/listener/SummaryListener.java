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
import java.util.concurrent.atomic.AtomicReference;
import nonapi.org.paramixel.listener.support.Constants;
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
 * <p><strong>Thread safety:</strong> The output writer is lazily initialized using an
 * {@code AtomicReference} with compare-and-set semantics to ensure single initialization.
 * Once initialized, concurrent writes to the underlying {@link PrintWriter} are safe.
 */
public final class SummaryListener implements Listener {

    private volatile SummaryRenderer renderer;
    private final AtomicReference<PrintWriter> out = new AtomicReference<>();
    private volatile boolean ansiEnabled;
    private volatile EnumSet<ExcludeTarget> excludes = EnumSet.noneOf(ExcludeTarget.class);

    /**
     * Creates a summary listener.
     */
    public SummaryListener() {}

    @Override
    public void initialize(final Configuration configuration) {
        if (renderer != null) {
            return;
        }
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

    /**
     * Prints a version banner and starting message to the output writer.
     */
    @Override
    public void onRunStarted() {
        if (excludes.contains(ExcludeTarget.SUMMARY_HEADER)) return;
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
        String versionLine = "Paramixel v" + version;

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
            String statusLine = "Status      : " + Listeners.formatStatus(result);
            String finishedLine =
                    "Finished at : " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String timeLine = "Total time  : " + ElapsedTimeFormatter.formatElapsedTime(Listeners.elapsedMillis(root));

            int maxLineLength = Math.max(
                    Math.max(versionLine.length(), statusLine.length()),
                    Math.max(finishedLine.length(), timeLine.length()));
            String dashes = "-".repeat(maxLineLength);

            writer.println(prefix + dashes);
            writer.println(prefix + versionLine);
            writer.println(prefix + dashes);
            writer.println(prefix + "Status      : " + formatStatus(result));
            writer.println(prefix + "Finished at : "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println(
                    prefix + "Total time  : " + ElapsedTimeFormatter.formatElapsedTime(Listeners.elapsedMillis(root)));
            writer.println(prefix + dashes);
        }
    }

    private String getPrefix() {
        return ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
    }

    private PrintWriter getWriter() {
        var w = out.get();
        if (w == null) {
            var newWriter = new PrintWriter(System.out, true);
            if (!out.compareAndSet(null, newWriter)) {
                newWriter.close();
            }
        }
        return out.get();
    }

    private String formatStatus(final Result result) {
        return ansiEnabled ? Listeners.formatAnsiStatus(result) : Listeners.formatStatus(result);
    }
}
