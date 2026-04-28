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
import org.paramixel.core.internal.util.AnsiColor;

public class StatusListener implements Listener {

    private static final String PARAMIXEL = Listeners.PARAMIXEL;

    public StatusListener() {}

    @Override
    public void beforeAction(Context context, Action action) {
        System.out.println(PARAMIXEL + buildDisplayName(context, action));
    }

    @Override
    public void afterAction(Context context, Action action, Result result) {
        System.out.println(PARAMIXEL + formatStatus(result.status()) + " " + buildDisplayName(context, action) + " "
                + formatTiming(result.timing()));
    }

    private static String buildActionPath(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        Deque<String> names = new ArrayDeque<>();
        Context current = context;
        Action previous = null;
        while (current != null) {
            Action currentAction = current.action();
            if (!Objects.equals(currentAction, previous)) {
                names.addFirst(currentAction.name());
                previous = currentAction;
            }
            current = current.parent().orElse(null);
        }
        return String.join(" / ", names);
    }

    private static String buildDisplayName(Context context, Action action) {
        Class<?> actionClass = action.getClass();
        String name = action.getClass().getName();
        String kind;

        if (actionClass.getPackageName().equals("org.paramixel.core.action")) {
            kind = actionClass.getSimpleName();
        } else {
            kind = name;
        }

        return buildActionPath(context) + " (" + kind + ")";
    }

    private static String formatStatus(Result.Status status) {
        Objects.requireNonNull(status, "status must not be null");
        return switch (status) {
            case PASS -> AnsiColor.GREEN_TEXT.format("PASS");
            case FAIL -> AnsiColor.RED_TEXT.format("FAIL");
            case SKIP -> AnsiColor.YELLOW_TEXT.format("SKIP");
        };
    }

    private static String formatTiming(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return timing.toMillis() + " ms";
    }
}
