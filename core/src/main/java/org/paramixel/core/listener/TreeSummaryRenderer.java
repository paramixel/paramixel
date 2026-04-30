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
import org.paramixel.core.Action;
import org.paramixel.core.Runner;
import org.paramixel.core.Status;
import org.paramixel.core.support.AnsiColor;

class TreeSummaryRenderer implements SummaryRenderer {

    private static final String PARAMIXEL = Listeners.PARAMIXEL;

    /**
     * {@inheritDoc}
     *
     * <p>This implementation renders the action hierarchy as an indented tree.</p>
     */
    @Override
    public void renderSummary(Runner runner, Action action) {
        System.out.println(PARAMIXEL);
        String status = formatStatus(action.getResult().getStatus());
        String actionName = action.getName();
        String kind = formatKind(action);
        String timing = formatTiming(action.getResult().getElapsedTime());
        String failureInfo = formatFailureInfo(action.getResult().getStatus());
        System.out.println(PARAMIXEL + status + " " + actionName + " (" + kind + ") " + timing + failureInfo);

        List<Action> children = action.getChildren();
        for (int i = 0; i < children.size(); i++) {
            renderTree(children.get(i), "", i == children.size() - 1);
        }
    }

    private void renderTree(Action action, String prefix, boolean isLast) {
        String status = formatStatus(action.getResult().getStatus());
        String actionName = action.getName();
        String kind = formatKind(action);
        String timing = formatTiming(action.getResult().getElapsedTime());
        String failureInfo = formatFailureInfo(action.getResult().getStatus());

        String connector = isLast ? "└── " : "├── ";
        String line = prefix + connector + status + " " + actionName + " (" + kind + ") " + timing + failureInfo;
        System.out.println(PARAMIXEL + line);

        List<Action> children = action.getChildren();
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

    private String formatKind(Action action) {
        Class<?> actionClass = action.getClass();
        if (actionClass.getPackageName().equals("org.paramixel.core.action")) {
            return actionClass.getSimpleName();
        }
        return actionClass.getName();
    }
}
