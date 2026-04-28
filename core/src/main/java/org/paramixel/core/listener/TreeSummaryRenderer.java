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
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.internal.util.AnsiColor;

class TreeSummaryRenderer implements SummaryRenderer {

    private static final String PARAMIXEL = Listeners.PARAMIXEL;

    @Override
    public void renderSummary(Runner runner, Result result) {
        System.out.println(PARAMIXEL);
        String status = formatStatus(result.status());
        String actionName = result.action().name();
        String kind = formatKind(result.action());
        String timing = formatTiming(result.timing());
        String failureInfo = result.status() == Result.Status.FAIL
                ? result.failure()
                        .map(f -> " → " + f.getClass().getSimpleName() + ": " + f.getMessage())
                        .orElse("")
                : result.status() == Result.Status.SKIP
                        ? result.failure().map(f -> " → " + f.getMessage()).orElse("")
                        : "";
        System.out.println(PARAMIXEL + status + " " + actionName + " (" + kind + ") " + timing + failureInfo);

        List<Result> children = result.children();
        for (int i = 0; i < children.size(); i++) {
            renderTree(children.get(i), "", i == children.size() - 1);
        }
        System.out.println(PARAMIXEL);
    }

    private void renderTree(Result result, String prefix, boolean isLast) {
        String status = formatStatus(result.status());
        String actionName = result.action().name();
        String kind = formatKind(result.action());
        String timing = formatTiming(result.timing());
        String failureInfo = result.status() == Result.Status.FAIL
                ? result.failure()
                        .map(f -> " → " + f.getClass().getSimpleName() + ": " + f.getMessage())
                        .orElse("")
                : result.status() == Result.Status.SKIP
                        ? result.failure().map(f -> " → " + f.getMessage()).orElse("")
                        : "";

        String connector = isLast ? "└── " : "├── ";
        String line = prefix + connector + status + " " + actionName + " (" + kind + ") " + timing + failureInfo;
        System.out.println(PARAMIXEL + line);

        List<Result> children = result.children();
        if (!children.isEmpty()) {
            String childPrefix = prefix + (isLast ? "    " : "│   ");
            for (int i = 0; i < children.size(); i++) {
                renderTree(children.get(i), childPrefix, i == children.size() - 1);
            }
        }
    }

    private String formatStatus(Result.Status status) {
        Objects.requireNonNull(status, "status must not be null");
        return switch (status) {
            case PASS -> AnsiColor.GREEN_TEXT.format("PASS");
            case FAIL -> AnsiColor.RED_TEXT.format("FAIL");
            case SKIP -> AnsiColor.YELLOW_TEXT.format("SKIP");
        };
    }

    private String formatTiming(Duration timing) {
        Objects.requireNonNull(timing, "timing must not be null");
        return timing.toMillis() + " ms";
    }

    private String formatKind(org.paramixel.core.Action action) {
        Class<?> actionClass = action.getClass();
        if (actionClass.getPackageName().equals("org.paramixel.core.action")) {
            return actionClass.getSimpleName();
        }
        return actionClass.getName();
    }
}
