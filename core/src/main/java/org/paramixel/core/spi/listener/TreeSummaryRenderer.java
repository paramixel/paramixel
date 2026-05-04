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

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;
import org.paramixel.core.support.AnsiColor;

/**
 * Renders a tree-style summary that preserves action nesting.
 *
 * <p>Each line includes the action status, name, kind, timing, and failure or skip detail when available.
 */
public class TreeSummaryRenderer implements SummaryRenderer {

    private static final String HIDDEN_ROOT = "7e5c6b4c-1428-3fee-abd4-24a245687061";

    @Override
    public void renderSummary(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");
        List<Result> topResults;
        if (HIDDEN_ROOT.equals(result.getAction().getName())) {
            topResults = result.getChildren();
        } else {
            topResults = List.of(result);
        }
        for (int i = 0; i < topResults.size(); i++) {
            renderTree(topResults.get(i), "", i == topResults.size() - 1);
        }
    }

    private void renderTree(Result result, String prefix, boolean isLast) {
        String status = formatStatus(result.getStatus());
        String actionName = result.getAction().getName();
        String kind = formatKind(result.getAction());
        String timing = formatTiming(result.getElapsedTime());
        String failureInfo = formatFailureInfo(result.getStatus());

        String connector = isLast ? "└── " : "├── ";
        String line = prefix + connector + status + " " + actionName + " (" + kind + ") " + timing + failureInfo;
        System.out.println(Constants.PARAMIXEL + line);

        List<Result> children = result.getChildren();
        if (!children.isEmpty()) {
            String childPrefix = prefix + (isLast ? "    " : "│   ");
            for (int i = 0; i < children.size(); i++) {
                renderTree(children.get(i), childPrefix, i == children.size() - 1);
            }
        }
    }

    private String formatStatus(Status status) {
        Objects.requireNonNull(status, "status must not be null");
        if (status.isStaged()) {
            return AnsiColor.BOLD_GRAY_TEXT.format("STAGED");
        } else if (status.isPass()) {
            return AnsiColor.BOLD_GREEN_TEXT.format("PASS");
        } else if (status.isFailure()) {
            return AnsiColor.BOLD_RED_TEXT.format("FAIL");
        } else {
            return AnsiColor.BOLD_ORANGE_TEXT.format("SKIP");
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
