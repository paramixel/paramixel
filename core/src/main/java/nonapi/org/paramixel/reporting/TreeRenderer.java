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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.paramixel.api.Failure;
import org.paramixel.api.Status;
import org.paramixel.api.TaskResult;

/**
 * Renders an immutable result tree as tree-shaped text.
 *
 * <p>Each node is rendered as its name, its scheduling mode and phase qualifiers, then its outcome: the short status
 * label, the elapsed time, the attempt history, and the reason. Ancestors and siblings are drawn with box-drawing
 * connectors at the left margin, so the hierarchy is visible without padding status labels into a fixed column.
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

    /** The branch drawn before a child that has a later sibling. */
    private static final String BRANCH = "├── ";

    /** The branch drawn before the last child of a node. */
    private static final String LAST_BRANCH = "└── ";

    /** The continuation drawn under a node that has a later sibling. */
    private static final String CONTINUATION = "│   ";

    /** The blank continuation drawn under the last child of a node. */
    private static final String BLANK = "    ";

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
        append(lines, root, "", true, true, console, mode, null);
        return lines;
    }

    private static void append(
            final List<String> lines,
            final TaskResult result,
            final String prefix,
            final boolean last,
            final boolean root,
            final ConsoleSupport console,
            final Mode mode,
            final AttemptInfo attemptInfo) {
        final var attempts = attemptsOf(result);
        final var connector = root ? "" : last ? LAST_BRANCH : BRANCH;
        final var attemptPrefix =
                attemptInfo == null ? "" : "attempt " + attemptInfo.index() + " of " + attemptInfo.total() + " ";
        final var superseded = attemptInfo != null && attemptInfo.superseded() ? " — superseded" : "";
        final var history = historyOf(attempts);
        final var message = result.reason().map(reason -> " — " + reason).orElse("");
        final var modeText = result.kind() == TaskResult.Kind.TASK && schedulesChildren(result)
                ? " (" + result.mode().name().toLowerCase(Locale.ROOT) + ")"
                : "";
        // A subtree given to a before or after slot is rendered under its owner and labelled with that phase. Plan
        // parameters are report-only, so they never appear on a console line.
        final var slotText = result.slot() == TaskResult.Slot.CHILD
                ? ""
                : " [" + result.slot().name().toLowerCase(Locale.ROOT) + "]";
        final var elapsed = elapsedOf(result, console);
        lines.add(prefix
                + connector
                + attemptPrefix
                + result.name()
                + modeText
                + slotText
                + " "
                + console.label(result.status())
                + elapsed
                + superseded
                + history
                + message);
        final var childPrefix = prefix + (root ? "" : last ? BLANK : CONTINUATION);
        final var children = visibleChildren(result, mode, attempts);
        for (int i = 0; i < children.size(); i++) {
            final var child = children.get(i);
            final var childInfo = child.kind() == TaskResult.Kind.ATTEMPT
                    ? new AttemptInfo(child.attemptIndex(), attempts.size(), child.attemptIndex() < attempts.size())
                    : null;
            append(lines, child, childPrefix, i == children.size() - 1, false, console, mode, childInfo);
        }
    }

    /**
     * Returns the children that the selected mode prints under a node.
     *
     * <p>Console output shows each occurrence once, so a retried or repeated occurrence is represented by its last
     * attempt and the attempt nodes themselves are not printed. Detail output prints the attempt nodes.
     *
     * @param result the node; must not be {@code null}
     * @param mode the rendering mode; must not be {@code null}
     * @param attempts the attempt children of the node; must not be {@code null}
     * @return the children to render; never {@code null}
     */
    private static List<TaskResult> visibleChildren(
            final TaskResult result, final Mode mode, final List<TaskResult> attempts) {
        if (mode == Mode.CONSOLE && !attempts.isEmpty()) {
            return attempts.get(attempts.size() - 1).children();
        }
        return result.children();
    }

    /**
     * Returns the elapsed-time suffix of one node.
     *
     * <p>Execution and finalization are measured on the engine's monotonic clock and clamped at zero. Their sum is the
     * span from admission to completion for a task, which is the span a live status line reports, and the span from
     * attempt start to completion for an attempt node. A node that never ran has neither duration, so it has no time.
     *
     * @param result the node; must not be {@code null}
     * @param console the console formatting; must not be {@code null}
     * @return the suffix, or an empty string when no duration was measured; never {@code null}
     */
    private static String elapsedOf(final TaskResult result, final ConsoleSupport console) {
        final var execution = result.executionDuration();
        final var finalization = result.finalizationDuration();
        if (execution.isEmpty() && finalization.isEmpty()) {
            return "";
        }
        final var elapsed = execution.orElse(Duration.ZERO).plus(finalization.orElse(Duration.ZERO));
        return console.elapsed(elapsed);
    }

    /**
     * Returns whether a result schedules child tasks.
     *
     * <p>A mode describes how children are scheduled, so it says nothing about a leaf task that only runs a body.
     * Attempt nodes are history rather than structure and are looked through: a retried group still has children, and
     * a retried leaf still has none.
     *
     * @param result the result to inspect; must not be {@code null}
     * @return {@code true} when the result schedules at least one child task
     */
    private static boolean schedulesChildren(final TaskResult result) {
        for (TaskResult child : result.children()) {
            if (child.kind() == TaskResult.Kind.TASK || schedulesChildren(child)) {
                return true;
            }
        }
        return false;
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
