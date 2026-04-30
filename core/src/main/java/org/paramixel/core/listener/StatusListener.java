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

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.support.AnsiColor;

/**
 * A {@link Listener} implementation that logs action execution status to standard output.
 *
 * <p>This listener provides a simple, human-readable console view of execution progress:
 * <ul>
 *     <li>Before execution: prints a "TEST" line with the action display name</li>
 *     <li>After execution: prints the final {@link Status}, action display name, and elapsed time</li>
 *     <li>On error: prints the stack trace to {@code System.err}</li>
 * </ul>
 *
 * <p>Output is formatted with ANSI colors for improved readability in supported terminals:
 * <ul>
 *     <li>PASS → green</li>
 *     <li>FAIL → red</li>
 *     <li>SKIP → yellow</li>
 *     <li>Other states → gray</li>
 * </ul>
 *
 * <p>The display name includes the hierarchical action path along with a resolved
 * "kind" (either the simple class name for core actions or the fully qualified name
 * for external/custom actions).
 *
 * <p>This class is stateless and thread-safe assuming {@link Action} and related
 * objects are used safely by the caller.
 */
public class StatusListener implements Listener {

    /**
     * Prefix used for all output lines, typically branding or framework identifier.
     */
    private static final String PARAMIXEL = Listeners.PARAMIXEL;

    /**
     * Creates a new status listener.
     *
     * @return a new status listener; never {@code null}
     */
    public static StatusListener of() {
        return new StatusListener();
    }

    /**
     * Default constructor.
     */
    private StatusListener() {}

    /**
     * Invoked before an {@link Action} is executed.
     *
     * <p>Logs a preliminary line indicating the action about to run.
     *
     * @param context the execution context
     * @param action  the action about to be executed (must not be {@code null})
     */
    @Override
    public void beforeAction(Context context, Action action) {
        System.out.println(PARAMIXEL + "TEST | " + buildDisplayName(action));
    }

    /**
     * Invoked after an {@link Action} has completed.
     *
     * <p>Logs the final status, action display name, and execution duration.
     *
     * @param context the execution context
     * @param action  the action that was executed (must not be {@code null})
     * @param result  the result of the execution containing status and timing (must not be {@code null})
     */
    @Override
    public void afterAction(Context context, Action action, Result result) {
        System.out.println(PARAMIXEL
                + formatStatus(result.getStatus())
                + " | "
                + buildDisplayName(action)
                + " "
                + formatTiming(result.getElapsedTime()));
    }

    /**
     * Invoked when an {@link Action} throws an exception during execution.
     *
     * <p>This implementation prints the full stack trace to {@code System.err}.
     *
     * @param context   the execution context
     * @param action    the action that threw the exception
     * @param throwable the thrown exception
     */
    @Override
    public void actionThrowable(Context context, Action action, Throwable throwable) {
        throwable.printStackTrace(System.err);
    }

    /**
     * Builds a human-readable display name for an {@link Action}.
     *
     * <p>The display name consists of:
     * <ul>
     *     <li>The hierarchical action path (parent → child)</li>
     *     <li>A "kind" identifier in parentheses</li>
     * </ul>
     *
     * <p>The kind is determined as follows:
     * <ul>
     *     <li>If the action is part of the core package, its simple class name is used</li>
     *     <li>Otherwise, the fully qualified class name is used</li>
     * </ul>
     *
     * @param action the action to describe (must not be {@code null})
     * @return a formatted display name string
     * @throws NullPointerException if {@code action} is {@code null}
     */
    private static String buildDisplayName(Action action) {
        Objects.requireNonNull(action, "action must not be null");
        Class<?> actionClass = action.getClass();
        String name = action.getClass().getName();
        String kind;

        if (actionClass.getPackageName().startsWith("org.paramixel.core.action")) {
            kind = actionClass.getSimpleName();
        } else {
            kind = name;
        }

        return buildActionPath(action) + " (" + kind + ")";
    }

    /**
     * Builds the hierarchical path for an {@link Action}.
     *
     * <p>The path is constructed by traversing the action's parent chain,
     * starting from the root and ending with the current action.
     *
     * <p>Each segment is separated by {@code " / "} to indicate hierarchy.
     *
     * @param action the action whose path is to be built
     * @return a string representing the full action path
     */
    private static String buildActionPath(Action action) {
        Deque<String> names = new ArrayDeque<>();
        Action current = action;
        while (current != null) {
            names.addFirst(current.getName());
            current = current.getParent().orElse(null);
        }
        return String.join(" / ", names);
    }

    /**
     * Formats a {@link Status} into a colored string representation.
     *
     * <p>The mapping is:
     * <ul>
     *     <li>{@code PASS} → bold green</li>
     *     <li>{@code FAIL} → bold red</li>
     *     <li>{@code SKIP} → bold yellow</li>
     *     <li>Other → bold gray (treated as "STAGED")</li>
     * </ul>
     *
     * @param status the status to format (must not be {@code null})
     * @return a color-formatted status string
     * @throws NullPointerException if {@code status} is {@code null}
     */
    private static String formatStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        if (status.isPass()) {
            return AnsiColor.BOLD_GREEN_TEXT.format("PASS");
        } else if (status.isFailure()) {
            return AnsiColor.BOLD_RED_TEXT.format("FAIL");
        } else if (status.isSkip()) {
            return AnsiColor.BOLD_ORANGE_TEXT.format("SKIP");
        } else {
            return AnsiColor.BOLD_GRAY_TEXT.format("STAGED");
        }
    }

    /**
     * Formats an execution {@link Duration} into milliseconds.
     *
     * @param timing the duration to format (must not be {@code null})
     * @return a string in the format {@code "<millis> ms"}
     * @throws NullPointerException if {@code timing} is {@code null}
     */
    private static String formatTiming(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return timing.toMillis() + " ms";
    }
}
