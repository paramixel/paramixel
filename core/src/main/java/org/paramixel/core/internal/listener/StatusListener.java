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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.internal.ActionHierarchy;

/**
 * Prints per-action status updates to the console.
 *
 * <p>This listener emits a line when an action starts, completes, is skipped, or reports a throwable.
 */
public class StatusListener implements Listener {

    private static final String ROOT_NAME = Constants.ROOT_NAME;

    private static final Object OUTPUT_LOCK = new Object();

    private final boolean ansiEnabled;

    /**
     * Creates a console status listener with ANSI formatting enabled.
     */
    public StatusListener() {
        this(true);
    }

    /**
     * Creates a console status listener.
     *
     * @param ansiEnabled whether ANSI formatting should be used
     */
    public StatusListener(boolean ansiEnabled) {
        this.ansiEnabled = ansiEnabled;
    }

    private String getPrefix() {
        return ansiEnabled ? Constants.PARAMIXEL_ANSI : Constants.PARAMIXEL_PLAIN;
    }

    @Override
    public void beforeAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        if (ROOT_NAME.equals(result.getAction().getName())) {
            return;
        }
        String output = getPrefix() + "RUN  | " + buildIdPath(result) + " | " + buildDisplayName(result);
        synchronized (OUTPUT_LOCK) {
            System.out.println(output);
        }
    }

    @Override
    public void afterAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        if (ROOT_NAME.equals(result.getAction().getName())) {
            return;
        }
        String output = getPrefix()
                + Listeners.formatAnsiStatus(result.getStatus())
                + " | "
                + buildIdPath(result)
                + " | "
                + buildDisplayName(result)
                + " "
                + formatTiming(result.getRunDuration());
        synchronized (OUTPUT_LOCK) {
            System.out.println(output);
        }
    }

    @Override
    public void skipAction(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        if (ROOT_NAME.equals(result.getAction().getName())) {
            return;
        }
        String output = getPrefix()
                + Listeners.formatAnsiStatus(result.getStatus())
                + " | "
                + buildIdPath(result)
                + " | "
                + buildDisplayName(result)
                + " "
                + formatTiming(result.getRunDuration());
        synchronized (OUTPUT_LOCK) {
            System.out.println(output);
        }
    }

    @Override
    public void actionThrowable(Result result, Throwable throwable) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(throwable, "throwable must not be null");
        if (ROOT_NAME.equals(result.getAction().getName())) {
            return;
        }
        var sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        synchronized (OUTPUT_LOCK) {
            System.err.println(getPrefix() + "EXCEPTION | " + buildIdPath(result) + " | " + buildDisplayName(result));
            System.err.print(trace);
        }
    }

    private static String buildDisplayName(Result result) {
        Objects.requireNonNull(result, "result must not be null");
        Action action = result.getAction();
        Class<?> actionClass = action.getClass();
        String name = actionClass.getName();
        String kind;

        if (actionClass.getPackageName().startsWith(Constants.ACTION_PACKAGE_PREFIX)) {
            kind = actionClass.getSimpleName();
        } else {
            kind = name;
        }

        return buildActionPath(result) + " (" + kind + ")";
    }

    private static String buildActionPath(Result result) {
        var indexedPath = ActionHierarchy.pathOf(result.getAction());
        if (indexedPath.isPresent()) {
            var indexedNames = new ArrayDeque<String>();
            for (Action action : indexedPath.orElseThrow()) {
                if (!ROOT_NAME.equals(action.getName())) {
                    indexedNames.addLast(action.getName());
                }
            }
            return String.join(" / ", indexedNames);
        }

        var names = new ArrayDeque<String>();
        Result current = result;
        while (current != null) {
            Action action = current.getAction();
            if (!ROOT_NAME.equals(action.getName())) {
                names.addFirst(action.getName());
            }
            current = current.getParent().orElse(null);
        }
        return String.join(" / ", names);
    }

    private static String buildIdPath(Result result) {
        var indexedPath = ActionHierarchy.pathOf(result.getAction());
        if (indexedPath.isPresent()) {
            var indexedIds = new ArrayDeque<String>();
            for (Action action : indexedPath.orElseThrow()) {
                if (!ROOT_NAME.equals(action.getName())) {
                    indexedIds.addLast(action.getId());
                }
            }
            return String.join("-", indexedIds);
        }

        var ids = new ArrayDeque<String>();
        Result current = result;
        while (current != null) {
            Action action = current.getAction();
            if (!ROOT_NAME.equals(action.getName())) {
                ids.addFirst(action.getId());
            }
            current = current.getParent().orElse(null);
        }
        return String.join("-", ids);
    }

    private static String formatTiming(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return timing.toMillis() + " ms";
    }
}
