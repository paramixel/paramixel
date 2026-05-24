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

package org.paramixel.api.internal.listener;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.Version;
import org.paramixel.api.internal.listener.support.Constants;
import org.paramixel.api.internal.support.ElapsedTimeFormatter;

/**
 * Prints a descriptor tree summary when a run completes, preceded by a starting banner and
 * followed by a footer with status, timestamp, and total elapsed time.
 *
 * <p>The action tree rendering is delegated to a {@link SummaryRenderer}. Each rendered line is
 * prefixed with the {@code [PARAMIXEL]} tag (ANSI-colored or plain depending on configuration).
 *
 * <p><strong>Thread safety:</strong> The output writer is lazily initialized and stored in a
 * {@code volatile} field. Concurrent callbacks may race to initialize the writer, resulting in a
 * harmless duplicate allocation. Once initialized, concurrent writes to the underlying
 * {@link PrintWriter} are safe.
 */
public final class SummaryListener implements Listener {

    private final SummaryRenderer renderer;
    private volatile PrintWriter out;
    private final boolean ansiEnabled;

    /**
     * Creates a summary listener using the supplied renderer with ANSI formatting enabled by default.
     *
     * @param renderer the renderer used to produce the action tree summary
     */
    public SummaryListener(final SummaryRenderer renderer) {
        this(renderer, true);
    }

    /**
     * Creates a summary listener using the supplied renderer with configurable ANSI formatting.
     *
     * @param renderer the renderer used to produce the action tree summary
     * @param ansiEnabled whether ANSI color formatting should be applied
     */
    public SummaryListener(final SummaryRenderer renderer, final boolean ansiEnabled) {
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
        this.ansiEnabled = ansiEnabled;
    }

    private PrintWriter getWriter() {
        var w = out;
        if (w == null) {
            w = new PrintWriter(System.out, true);
            out = w;
        }
        return w;
    }

    private String getPrefix() {
        return ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
    }

    /**
     * Prints a version banner and starting message to the output writer.
     */
    @Override
    public void onRunStarted() {
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
        var writer = getWriter();
        var prefix = getPrefix();

        var optionalRoot = result.descriptor();
        if (optionalRoot.isEmpty()) {
            writer.println(prefix + "No Paramixel tests found");
            return;
        }

        var root = optionalRoot.get();

        var version = Version.version();
        String versionLine = "Paramixel v" + version;
        writer.println(prefix + "-".repeat(versionLine.length()));
        writer.println(prefix + versionLine);

        var output = renderer.render(root);
        for (var line : output.split(System.lineSeparator())) {
            if (!line.isBlank()) {
                writer.println(prefix + line);
            }
        }

        String statusLine = "Status      : " + Listeners.formatStatus(result.status());
        String finishedLine =
                "Finished at : " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String timeLine = "Total time  : "
                + ElapsedTimeFormatter.formatElapsedTime(
                        root.metadata().runDuration().toMillis());

        int maxLineLength = Math.max(
                Math.max(versionLine.length(), statusLine.length()),
                Math.max(finishedLine.length(), timeLine.length()));
        String dashes = "-".repeat(maxLineLength);

        writer.println(prefix + dashes);
        writer.println(prefix + versionLine);
        writer.println(prefix + dashes);
        writer.println(prefix + "Status      : " + formatStatus(result.status()));
        writer.println(prefix + "Finished at : "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        writer.println(prefix + "Total time  : "
                + ElapsedTimeFormatter.formatElapsedTime(
                        root.metadata().runDuration().toMillis()));
        writer.println(prefix + dashes);
    }

    private String formatStatus(final org.paramixel.api.Status status) {
        return ansiEnabled ? Listeners.formatAnsiStatus(status) : Listeners.formatStatus(status);
    }
}
