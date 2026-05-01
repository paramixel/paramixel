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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;
import org.paramixel.core.support.AnsiColor;

class TableSummaryRenderer implements SummaryRenderer {

    private static final int STATUS_WIDTH = 6;
    private static final int ACTION_WIDTH = 30;
    private static final int TIME_WIDTH = 10;
    private static final String PARAMIXEL = Listeners.PARAMIXEL;

    /**
     * {@inheritDoc}
     *
     * <p>This implementation renders a flattened summary table covering the entire action
     * tree and aggregate totals.</p>
     */
    @Override
    public void renderSummary(Runner runner, Action action) {
        List<Action> allActions = collectAllActions(action);
        long passed = allActions.stream()
                .filter(a -> a.getResult().getStatus().isPass())
                .count();
        long failed = allActions.stream()
                .filter(a -> a.getResult().getStatus().isFailure())
                .count();
        long skipped = allActions.stream()
                .filter(a -> a.getResult().getStatus().isSkip())
                .count();
        Duration totalTiming =
                allActions.stream().map(a -> a.getResult().getElapsedTime()).reduce(Duration.ZERO, Duration::plus);

        System.out.println(PARAMIXEL);
        System.out.println(PARAMIXEL + "Summary:");
        System.out.println(PARAMIXEL);

        for (Action a : allActions) {
            String status = formatStatus(a.getResult().getStatus());
            String actionName = truncate(a.getName(), ACTION_WIDTH);
            String kind = formatKind(a);
            String time = formatTiming(a.getResult().getElapsedTime());
            String failureInfo = formatFailureInfo(a.getResult().getStatus());

            System.out.println(PARAMIXEL + " " + status + " " + String.format("%-" + ACTION_WIDTH + "s", actionName)
                    + " (" + kind + ") " + time + failureInfo);
        }

        System.out.println(PARAMIXEL);
        String resultSummary = String.format(
                "Results: %d passed, %d failed, %d skipped | Total: %dms",
                passed, failed, skipped, totalTiming.toMillis());
        System.out.println(PARAMIXEL + resultSummary);
    }

    private List<Action> collectAllActions(Action action) {
        List<Action> actions = new ArrayList<>();
        collectAllActions(action, actions);
        return actions;
    }

    private void collectAllActions(Action action, List<Action> actions) {
        if (action.getName() != Action.HIDDEN) {
            actions.add(action);
        }
        for (Action child : action.getChildren()) {
            collectAllActions(child, actions);
        }
    }

    private String formatStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        if (status.isStaged()) {
            return String.format("%-" + STATUS_WIDTH + "s", AnsiColor.BOLD_GRAY_TEXT.format("STAGED"));
        } else if (status.isPass()) {
            return String.format("%-" + STATUS_WIDTH + "s", AnsiColor.GREEN_TEXT.format("PASS"));
        } else if (status.isFailure()) {
            return String.format("%-" + STATUS_WIDTH + "s", AnsiColor.RED_TEXT.format("FAIL"));
        } else {
            return String.format("%-" + STATUS_WIDTH + "s", AnsiColor.ORANGE_TEXT.format("SKIP"));
        }
    }

    private String formatFailureInfo(Status status) {
        if (status.isFailure()) {
            return status.getThrowable()
                    .map(f -> " → " + f.getClass().getSimpleName() + ": " + f.getMessage())
                    .or(() -> status.getMessage().map(m -> " → " + m))
                    .orElse("");
        } else if (status.isSkip()) {
            return status.getMessage().map(reason -> " → " + reason).orElse("");
        } else {
            return "";
        }
    }

    private String formatTiming(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return String.format("%" + TIME_WIDTH + "d ms", timing.toMillis());
    }

    private String truncate(String s, int maxLength) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }

    private String formatKind(Action action) {
        Class<?> actionClass = action.getClass();
        if (actionClass.getPackageName().equals("org.paramixel.core.action")) {
            return actionClass.getSimpleName();
        }
        return actionClass.getName();
    }
}
