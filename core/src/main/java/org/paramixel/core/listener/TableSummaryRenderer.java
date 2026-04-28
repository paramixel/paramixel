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
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.internal.util.AnsiColor;

class TableSummaryRenderer implements SummaryRenderer {

    private static final int STATUS_WIDTH = 6;
    private static final int ACTION_WIDTH = 30;
    private static final int TIME_WIDTH = 10;
    private static final String PARAMIXEL = Listeners.PARAMIXEL;

    @Override
    public void renderSummary(Runner runner, Result result) {
        List<Result> allResults = collectAllResults(result);
        long passed = allResults.stream()
                .filter(r -> r.status() == Result.Status.PASS)
                .count();
        long failed = allResults.stream()
                .filter(r -> r.status() == Result.Status.FAIL)
                .count();
        long skipped = allResults.stream()
                .filter(r -> r.status() == Result.Status.SKIP)
                .count();
        Duration totalTiming = allResults.stream().map(Result::timing).reduce(Duration.ZERO, Duration::plus);

        System.out.println(PARAMIXEL);
        System.out.println(PARAMIXEL + "Summary:");
        System.out.println(PARAMIXEL);

        for (Result r : allResults) {
            String status = formatStatus(r.status());
            String action = truncate(r.action().name(), ACTION_WIDTH);
            String kind = formatKind(r.action());
            String time = formatTiming(r.timing());
            String failureInfo = r.status() == Result.Status.FAIL
                    ? r.failure()
                            .map(f -> " → " + f.getClass().getSimpleName() + ": " + f.getMessage())
                            .orElse("")
                    : r.status() == Result.Status.SKIP
                            ? r.failure().map(f -> " → " + f.getMessage()).orElse("")
                            : "";

            System.out.println(PARAMIXEL + " " + status + " " + String.format("%-" + ACTION_WIDTH + "s", action) + " ("
                    + kind + ") " + time + failureInfo);
        }

        System.out.println(PARAMIXEL);
        String resultSummary = String.format(
                "Results: %d passed, %d failed, %d skipped | Total: %dms",
                passed, failed, skipped, totalTiming.toMillis());
        System.out.println(PARAMIXEL + resultSummary);
    }

    private List<Result> collectAllResults(Result result) {
        List<Result> results = new ArrayList<>();
        collectAllResults(result, results);
        return results;
    }

    private void collectAllResults(Result result, List<Result> results) {
        results.add(result);
        for (Result child : result.children()) {
            collectAllResults(child, results);
        }
    }

    private String formatStatus(Result.Status status) {
        Objects.requireNonNull(status, "status must not be null");
        return switch (status) {
            case PASS -> String.format("%-" + STATUS_WIDTH + "s", AnsiColor.GREEN_TEXT.format("PASS"));
            case FAIL -> String.format("%-" + STATUS_WIDTH + "s", AnsiColor.RED_TEXT.format("FAIL"));
            case SKIP -> String.format("%-" + STATUS_WIDTH + "s", AnsiColor.YELLOW_TEXT.format("SKIP"));
        };
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
