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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.paramixel.api.Failure;
import org.paramixel.api.Status;
import org.paramixel.api.TaskResult;

/**
 * Renders an immutable result tree as indented text.
 *
 * <p>Every rendered line begins with the short status label at column 0; the tree indentation follows the label.
 *
 * <p>Two modes keep the console honest and the reports complete:
 *
 * <ul>
 *   <li>{@link Mode#CONSOLE} shows each occurrence once, with its final outcome, and summarizes attempt history on the
 *       occurrence line (for example {@code — 3 attempts, 2 failed}). Superseded attempts are not printed, so no console
 *       line reports {@code FAIL} for a run whose counters say {@code failed: 0}.</li>
 *   <li>{@link Mode#DETAIL} prints every attempt, annotated with {@code attempt k of n} and {@code superseded} for the
 *       historical ones, which is the right shape for a report file.</li>
 * </ul>
 */
final class TreeRenderer {

    /**
     * Rendering mode.
     */
    enum Mode {

        /** Show final outcomes only, with an attempt-history note. */
        CONSOLE,

        /** Show every attempt, marking superseded attempts. */
        DETAIL
    }

    private TreeRenderer() {
        // Intentionally empty
    }

    /**
     * Renders the console summary tree: final outcomes with attempt-history notes.
     *
     * @param root the root result; must not be {@code null}
     * @param console the console formatting; must not be {@code null}
     * @return the rendered lines; never {@code null}
     */
    static List<String> render(final TaskResult root, final ConsoleSupport console) {
        return render(root, console, Mode.CONSOLE);
    }

    /**
     * Renders the detailed report tree: every attempt, marking superseded attempts.
     *
     * @param root the root result; must not be {@code null}
     * @param console the console formatting; must not be {@code null}
     * @return the rendered lines; never {@code null}
     */
    static List<String> renderDetailed(final TaskResult root, final ConsoleSupport console) {
        return render(root, console, Mode.DETAIL);
    }

    /**
     * Renders the tree in the requested mode.
     *
     * @param root the root result; must not be {@code null}
     * @param console the console formatting; must not be {@code null}
     * @param mode the rendering mode; must not be {@code null}
     * @return the rendered lines; never {@code null}
     */
    static List<String> render(final TaskResult root, final ConsoleSupport console, final Mode mode) {
        final var lines = new ArrayList<String>();
        append(lines, root, 0, console, mode, null);
        return lines;
    }

    private static void append(
            final List<String> lines,
            final TaskResult result,
            final int depth,
            final ConsoleSupport console,
            final Mode mode,
            final AttemptInfo attemptInfo) {
        final var indent = "  ".repeat(depth);
        final var attempts = attemptsOf(result);
        final var label = console.label(result.status());
        final var attemptPrefix =
                attemptInfo == null ? "" : "attempt " + attemptInfo.index() + " of " + attemptInfo.total() + " ";
        final var superseded = attemptInfo != null && attemptInfo.superseded() ? " — superseded" : "";
        final var history = historyOf(attempts);
        final var message = result.reason().map(reason -> " — " + reason).orElse("");
        final var modeText = result.kind() == TaskResult.Kind.TASK
                ? " (" + result.mode().name().toLowerCase(Locale.ROOT) + ")"
                : "";
        lines.add(label + " " + indent + attemptPrefix + result.name() + modeText + superseded + history + message);
        if (mode == Mode.CONSOLE && !attempts.isEmpty()) {
            final var last = attempts.get(attempts.size() - 1);
            for (TaskResult child : last.children()) {
                append(lines, child, depth + 1, console, mode, null);
            }
            return;
        }
        final var children = result.children();
        for (TaskResult child : children) {
            final AttemptInfo childInfo = child.kind() == TaskResult.Kind.ATTEMPT
                    ? new AttemptInfo(child.attemptIndex(), attempts.size(), child.attemptIndex() < attempts.size())
                    : null;
            append(lines, child, depth + 1, console, mode, childInfo);
        }
    }

    private static List<TaskResult> attemptsOf(final TaskResult result) {
        return result.children().stream()
                .filter(child -> child.kind() == TaskResult.Kind.ATTEMPT)
                .toList();
    }

    private static String historyOf(final List<TaskResult> attempts) {
        if (attempts.size() < 2) {
            return "";
        }
        final var failed = attempts.stream()
                .filter(attempt -> attempt.status().kind() != Status.Kind.PASSED)
                .count();
        final var note = attempts.size() + " attempts" + (failed == 0 ? "" : ", " + failed + " failed");
        return " — " + note;
    }

    /**
     * Renders failure detail lines.
     *
     * @param result the root result; must not be {@code null}
     * @return the rendered lines; never {@code null}
     */
    static List<String> renderFailures(final TaskResult result) {
        final var lines = new ArrayList<String>();
        appendFailures(lines, result, 0);
        return lines;
    }

    private static void appendFailures(final List<String> lines, final TaskResult result, final int depth) {
        for (Failure failure : result.failures()) {
            lines.add("  ".repeat(depth) + result.path() + " [" + failure.phase() + "] " + failure.message());
            failure.throwable().ifPresent(throwable -> {
                var frameCount = 0;
                for (StackTraceElement element : throwable.getStackTrace()) {
                    if (frameCount++ >= 8) {
                        lines.add("  ".repeat(depth) + "    ...");
                        break;
                    }
                    lines.add("  ".repeat(depth) + "    at " + element);
                }
            });
        }
        for (TaskResult child : result.children()) {
            appendFailures(lines, child, depth + 1);
        }
    }

    /**
     * Describes one attempt within its sibling set.
     *
     * @param index the 1-based attempt index
     * @param total the number of attempts of the occurrence
     * @param superseded whether a later attempt replaced this one
     */
    private record AttemptInfo(int index, int total, boolean superseded) {}
}
