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

package org.paramixel.gui.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.HBox;

public final class ExecutionCell extends TreeCell<ExecutionNode> {

    private static final String ICON_COLOR_SUCCESS = "#4caf50";
    private static final String ICON_COLOR_FAILED = "#ef5350";
    private static final String ICON_COLOR_SKIPPED = "#ffa726";
    private static final String ICON_COLOR_RUNNING = "#66bb6a";
    private static final String ICON_COLOR_WAITING = "#757575";
    private static final String TEXT_COLOR = "#ffffff";
    private static final String BG_TRANSPARENT = "-fx-background-color: transparent;";

    private static final String ICON_SUCCESS = "\u2714";
    private static final String ICON_FAILED = "\u2718";
    private static final String ICON_SKIPPED = "\u23ED";
    private static final String ICON_RUNNING = "\u23F3";
    private static final String ICON_WAITING = "\u25CB";

    @Override
    protected void updateItem(ExecutionNode node, boolean empty) {
        super.updateItem(node, empty);

        if (empty || node == null) {
            setText(null);
            setGraphic(null);
            setStyle(BG_TRANSPARENT);
            setTooltip(null);
            return;
        }

        Label iconLabel = new Label(iconFor(node.status()));
        iconLabel.setStyle("-fx-text-fill: " + iconColorFor(node.status()) + "; " + BG_TRANSPARENT);

        String text = node.displayName();
        if (node.timing() != null && node.isCompleted()) {
            text += " (" + node.timing().toMillis() + " ms)";
        }
        Label textLabel = new Label(text);
        textLabel.setStyle("-fx-text-fill: " + TEXT_COLOR + "; " + BG_TRANSPARENT);

        HBox container = new HBox(iconLabel, textLabel);
        container.setStyle(BG_TRANSPARENT);
        container.setAlignment(Pos.CENTER_LEFT);

        setText(null);
        setGraphic(container);
        setStyle(BG_TRANSPARENT);

        Throwable throwable = node.throwable();
        String skipReason = node.skipReason();
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            setTooltip(new Tooltip(sw.toString()));
        } else if (skipReason != null) {
            setTooltip(new Tooltip("Skipped: " + skipReason));
        } else {
            setTooltip(null);
        }
    }

    private static String iconFor(ExecutionNode.Status status) {
        return switch (status) {
            case SUCCESSFUL -> ICON_SUCCESS;
            case FAILED -> ICON_FAILED;
            case SKIPPED -> ICON_SKIPPED;
            case RUNNING -> ICON_RUNNING;
            case WAITING -> ICON_WAITING;
        };
    }

    private static String iconColorFor(ExecutionNode.Status status) {
        return switch (status) {
            case SUCCESSFUL -> ICON_COLOR_SUCCESS;
            case FAILED -> ICON_COLOR_FAILED;
            case SKIPPED -> ICON_COLOR_SKIPPED;
            case RUNNING -> ICON_COLOR_RUNNING;
            case WAITING -> ICON_COLOR_WAITING;
        };
    }
}
