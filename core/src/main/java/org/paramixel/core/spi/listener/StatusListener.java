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

package org.paramixel.core.spi.listener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.support.AnsiColor;

/**
 * Prints per-action status updates to the console.
 *
 * <p>This listener emits a line when an action starts, completes, is skipped, or reports a throwable.
 */
public class StatusListener implements Listener {

    private static final String HIDDEN_ROOT = "7e5c6b4c-1428-3fee-abd4-24a245687061";

    private static final Object OUTPUT_LOCK = new Object();

    /**
     * Creates a console status listener.
     */
    public StatusListener() {}

    @Override
    public void beforeAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        if (HIDDEN_ROOT.equals(result.getAction().getName())) {
            return;
        }
        String output = Constants.PARAMIXEL + "RUN  | " + buildIdPath(result) + " | " + buildDisplayName(result);
        synchronized (OUTPUT_LOCK) {
            System.out.println(output);
        }
    }

    @Override
    public void afterAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        if (HIDDEN_ROOT.equals(result.getAction().getName())) {
            return;
        }
        String output = Constants.PARAMIXEL
                + formatStatus(result.getStatus())
                + " | "
                + buildIdPath(result)
                + " | "
                + buildDisplayName(result)
                + " "
                + formatTiming(result.getElapsedTime());
        synchronized (OUTPUT_LOCK) {
            System.out.println(output);
        }
    }

    @Override
    public void skipAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        if (HIDDEN_ROOT.equals(result.getAction().getName())) {
            return;
        }
        String output = Constants.PARAMIXEL
                + formatStatus(result.getStatus())
                + " | "
                + buildIdPath(result)
                + " | "
                + buildDisplayName(result)
                + " "
                + formatTiming(result.getElapsedTime());
        synchronized (OUTPUT_LOCK) {
            System.out.println(output);
        }
    }

    @Override
    public void actionThrowable(Result result, Throwable throwable) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(throwable, "throwable must not be null");
        if (HIDDEN_ROOT.equals(result.getAction().getName())) {
            return;
        }
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        synchronized (OUTPUT_LOCK) {
            System.err.println(
                    Constants.PARAMIXEL + "EXCEPTION | " + buildIdPath(result) + " | " + buildDisplayName(result));
            System.err.print(trace);
        }
    }

    private static String buildDisplayName(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        Action action = result.getAction();
        Class<?> actionClass = action.getClass();
        String name = action.getClass().getName();
        String kind;

        if (actionClass.getPackageName().startsWith("org.paramixel.core.action")) {
            kind = actionClass.getSimpleName();
        } else {
            kind = name;
        }

        return buildActionPath(result) + " (" + kind + ")";
    }

    private static String buildActionPath(Result result) {
        Deque<String> names = new ArrayDeque<>();
        Action current = result.getAction();
        while (current != null) {
            if (!HIDDEN_ROOT.equals(current.getName())) {
                names.addFirst(current.getName());
            }
            current = current.getParent().orElse(null);
        }
        return String.join(" / ", names);
    }

    private static String buildIdPath(Result result) {
        Deque<String> ids = new ArrayDeque<>();
        Action current = result.getAction();
        while (current != null) {
            if (!HIDDEN_ROOT.equals(current.getName())) {
                ids.addFirst(current.getId());
            }
            current = current.getParent().orElse(null);
        }
        return String.join("-", ids);
    }

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

    private static String formatTiming(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return timing.toMillis() + " ms";
    }
}
