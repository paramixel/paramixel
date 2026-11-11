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
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Task;
import org.paramixel.api.TaskEvent;
import org.paramixel.api.TaskPhase;

/**
 * Prints a status line for terminal and timeout events.
 *
 * <p>Output is written to the supplied stream from the dispatcher thread, so it never runs while the scheduler holds a
 * permit or lock. Phase events are not printed individually: the summary carries the phase detail.
 */
public final class StatusListener implements Listener {

    private final ConsoleSupport console;
    private final PrintStream out;

    /**
     * Creates a listener.
     *
     * @param configuration the effective configuration; must not be {@code null}
     * @param out the output stream; must not be {@code null}
     */
    public StatusListener(final Configuration configuration, final PrintStream out) {
        this.console = new ConsoleSupport(configuration);
        this.out = out;
    }

    @Override
    public void onRunStarted(final Configuration configuration) {
        if (!console.enabled("status.header")) {
            return;
        }
        final var runId = configuration.getString("paramixel.run.id").orElse("");
        out.println(console.line(
                console.color(AnsiColor.BOLD_WHITE_TEXT, "Paramixel " + org.paramixel.api.Version.version())
                        + (runId.isEmpty() ? "" : " " + runId)));
    }

    @Override
    public void onDiscoveryCompleted(final Optional<Task> root) {
        if (!console.enabled("status.header")) {
            return;
        }
        if (root.isEmpty()) {
            out.println(console.line(console.color(AnsiColor.BOLD_YELLOW_TEXT, "No task factories discovered")));
        }
    }

    @Override
    public void onWarning(final String message) {
        out.println(console.line(console.color(AnsiColor.BOLD_YELLOW_TEXT, "warn") + " " + message));
    }

    @Override
    public void onTaskEvent(final TaskEvent event) {
        if (event.phase() != TaskPhase.TIMEOUT && event.phase() != TaskPhase.TERMINAL) {
            return;
        }
        final var timing = event.elapsed()
                .map(elapsed -> " (" + elapsed.toMillis() + " ms)")
                .orElse("");
        final var reason =
                event.status().message().map(message -> " — " + message).orElse("");
        out.println(console.line(console.label(event.status()) + " " + event.path() + timing + reason));
    }

    @Override
    public void onRunCompleted(final org.paramixel.api.Result result) {
        if (!console.enabled("status.footer")) {
            return;
        }
        out.println(console.line(console.label(result.status()) + " run " + result.runId() + " in "
                + nonapi.org.paramixel.support.ElapsedTimeFormatter.formatDuration(elapsedMillis(result))
                + (result.isQuiescent() ? "" : " (non-quiescent)")));
    }

    private static long elapsedMillis(final org.paramixel.api.Result result) {
        final var start = result.startedAt();
        final var end = result.completedAt();
        if (start.isEmpty() || end.isEmpty()) {
            return 0L;
        }
        return java.time.Duration.between(start.orElseThrow(), end.orElseThrow())
                .toMillis();
    }
}
