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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;

/**
 * Renders a tree-style summary that preserves action nesting.
 *
 * <p>Each line includes the action status, name, kind, timing, and failure or skip detail when available.
 */
public class TreeSummaryRenderer implements SummaryRenderer {

    private final PrintWriter out;

    private final boolean ansiEnabled;

    private final boolean prefixEnabled;

    /**
     * Creates a tree summary renderer that writes to {@link System#out} with ANSI formatting enabled.
     */
    public TreeSummaryRenderer() {
        this.out = null;
        this.ansiEnabled = true;
        this.prefixEnabled = true;
    }

    /**
     * Creates a tree summary renderer for the supplied destination.
     *
     * @param out the output writer used for rendered summary lines
     * @param ansiEnabled whether ANSI formatting should be used
     */
    public TreeSummaryRenderer(final PrintWriter out, final boolean ansiEnabled) {
        this(out, ansiEnabled, true);
    }

    /**
     * Creates a tree summary renderer for the supplied destination.
     *
     * @param out the output writer used for rendered summary lines
     * @param ansiEnabled whether ANSI formatting should be used
     * @param prefixEnabled whether the {@code [PARAMIXEL]} prefix should be prepended to each line
     */
    public TreeSummaryRenderer(final PrintWriter out, final boolean ansiEnabled, final boolean prefixEnabled) {
        this.out = Objects.requireNonNull(out, "out must not be null");
        this.ansiEnabled = ansiEnabled;
        this.prefixEnabled = prefixEnabled;
    }

    /**
     * Creates a tree summary renderer for the supplied destination.
     *
     * @param out the output stream used for rendered summary lines
     * @param ansiEnabled whether ANSI formatting should be used
     */
    public TreeSummaryRenderer(final PrintStream out, final boolean ansiEnabled) {
        this(out, ansiEnabled, true);
    }

    /**
     * Creates a tree summary renderer for the supplied destination.
     *
     * @param out the output stream used for rendered summary lines
     * @param ansiEnabled whether ANSI formatting should be used
     * @param prefixEnabled whether the {@code [PARAMIXEL]} prefix should be prepended to each line
     */
    public TreeSummaryRenderer(final PrintStream out, final boolean ansiEnabled, final boolean prefixEnabled) {
        this.out = new PrintWriter(Objects.requireNonNull(out, "out must not be null"), true);
        this.ansiEnabled = ansiEnabled;
        this.prefixEnabled = prefixEnabled;
    }

    private PrintWriter out() {
        return out == null ? new PrintWriter(System.out, true) : out;
    }

    @Override
    public void renderSummary(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");
        String linePrefix = prefixEnabled ? (ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN) : "";
        renderTree(result, "", true, linePrefix);
    }

    private void renderTree(Result result, String prefix, boolean isLast, String linePrefix) {
        String status = formatStatus(result.getStatus());
        String actionName = result.getAction().getName();
        String kind = Listeners.formatKind(result.getAction());
        String timing = formatTiming(result.getRunDuration());
        String failureInfo = formatFailureInfo(result.getStatus());

        String connector = isLast ? "└─ " : "├─ ";
        String line = prefix + connector + status + " " + actionName + " (" + kind + ") " + timing + failureInfo;
        var writer = out();
        writer.println(linePrefix + line);

        List<Result> children = result.getChildren();
        if (!children.isEmpty()) {
            String childPrefix = prefix + (isLast ? "   " : "│  ");
            for (int i = 0; i < children.size(); i++) {
                renderTree(children.get(i), childPrefix, i == children.size() - 1, linePrefix);
            }
        }
    }

    private String formatStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        return ansiEnabled ? Listeners.formatAnsiStatus(status) : Listeners.formatStatus(status);
    }

    private String formatFailureInfo(Status status) {
        if (status.isFailure()) {
            return status.getThrowable()
                    .map(f -> " → " + f.getClass().getName() + ": " + Listeners.sanitizeMessage(f.getMessage()))
                    .or(() -> status.getMessage().map(m -> " → " + Listeners.sanitizeMessage(m)))
                    .orElse("");
        } else if (status.isSkip()) {
            return status.getMessage()
                    .map(reason -> " → " + Listeners.sanitizeMessage(reason))
                    .orElse("");
        } else {
            return "";
        }
    }

    private String formatTiming(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return timing.toMillis() + " ms";
    }
}
