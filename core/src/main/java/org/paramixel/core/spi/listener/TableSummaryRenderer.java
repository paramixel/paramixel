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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;
import org.paramixel.core.support.AnsiColor;

/**
 * Renders a flat table-style summary of all completed actions.
 *
 * <p>The rendered output includes one line per action followed by aggregate pass/fail/skip counts.
 */
public class TableSummaryRenderer implements SummaryRenderer {

    private static final String HIDDEN_ROOT = "7e5c6b4c-1428-3fee-abd4-24a245687061";

    private static final int STATUS_WIDTH = 6;
    private static final int ACTION_WIDTH = 30;
    private static final int TIME_WIDTH = 10;

    @Override
    public void renderSummary(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");
        List<Result> allResults = collectAllResults(result);
        long passed = allResults.stream().filter(r -> r.getStatus().isPass()).count();
        long failed = allResults.stream().filter(r -> r.getStatus().isFailure()).count();
        long skipped = allResults.stream().filter(r -> r.getStatus().isSkip()).count();
        Duration totalTiming = result.getElapsedTime();

        System.out.println(Constants.PARAMIXEL);
        System.out.println(Constants.PARAMIXEL + "Summary:");
        System.out.println(Constants.PARAMIXEL);

        for (Result r : allResults) {
            String status = formatStatus(r.getStatus());
            String actionName = truncate(r.getAction().getName(), ACTION_WIDTH);
            String kind = formatKind(r.getAction());
            String time = formatTiming(r.getElapsedTime());
            String failureInfo = formatFailureInfo(r.getStatus());

            System.out.println(Constants.PARAMIXEL + " " + status + " "
                    + String.format("%-" + ACTION_WIDTH + "s", actionName) + " (" + kind + ") " + time + failureInfo);
        }

        System.out.println(Constants.PARAMIXEL);
        String resultSummary = String.format(
                "Results: %d passed, %d failed, %d skipped | Total: %dms",
                passed, failed, skipped, totalTiming.toMillis());
        System.out.println(Constants.PARAMIXEL + resultSummary);
    }

    private List<Result> collectAllResults(Result result) {
        List<Result> results = new ArrayList<>();
        collectAllResults(result, results);
        return results;
    }

    private void collectAllResults(Result result, List<Result> results) {
        if (!HIDDEN_ROOT.equals(result.getAction().getName())) {
            results.add(result);
        }
        for (Result child : result.getChildren()) {
            collectAllResults(child, results);
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

    private String formatKind(org.paramixel.core.Action action) {
        Class<?> actionClass = action.getClass();
        if (actionClass.getPackageName().equals("org.paramixel.core.action")) {
            return actionClass.getSimpleName();
        }
        return actionClass.getName();
    }
}
