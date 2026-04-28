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

package org.paramixel.gui;

import java.io.PrintWriter;
import java.io.StringWriter;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import org.paramixel.gui.DiscoveryNode.Kind;
import org.paramixel.gui.DiscoveryNode.Status;

public final class DiscoveryCell extends TreeCell<DiscoveryNode> {

    private static final String ICON_SUCCESS = "\u2714";
    private static final String ICON_FAILED = "\u2718";
    private static final String ICON_SKIPPED = "\u23ED";
    private static final String ICON_RUNNING = "\u23F3";
    private static final String ICON_NOT_RUN = "\u25CB";

    @Override
    protected void updateItem(DiscoveryNode node, boolean empty) {
        super.updateItem(node, empty);

        if (empty || node == null) {
            setText(null);
            setGraphic(null);
            setStyle(null);
            setTooltip(null);
            setContextMenu(null);
            return;
        }

        Label iconLabel = new Label(iconFor(node.status()));
        iconLabel.getStyleClass().add("status-icon-" + statusStyleClass(node.status()));
        iconLabel.setStyle("-fx-background-color: transparent;");

        Label textLabel = new Label(node.displayName());
        textLabel.setStyle("-fx-background-color: transparent;");

        HBox container = new HBox(iconLabel, textLabel);
        container.setStyle("-fx-background-color: transparent;");
        container.setAlignment(Pos.CENTER_LEFT);

        setText(null);
        setGraphic(container);
        setStyle("-fx-background-color: transparent;");

        setContextMenu(createContextMenu(node));

        Throwable throwable = node.throwable();
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            setTooltip(new Tooltip(sw.toString()));
        } else {
            setTooltip(new Tooltip(node.qualifiedName()));
        }
    }

    private javafx.scene.control.ContextMenu createContextMenu(DiscoveryNode node) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();

        if (node.kind() == Kind.ACTION) {
            MenuItem runItem = new MenuItem("Run Action");
            runItem.setOnAction(e -> GuiRunner.getInstance().runNode(node));
            menu.getItems().add(runItem);

            if (node.status() != Status.NOT_RUN) {
                MenuItem rerunItem = new MenuItem("Re-run");
                rerunItem.setOnAction(e -> GuiRunner.getInstance().runNode(node));
                menu.getItems().add(rerunItem);
            }
        } else if (node.kind() == Kind.CLASS) {
            MenuItem runItem = new MenuItem("Run Class");
            runItem.setOnAction(e -> GuiRunner.getInstance().runNode(node));
            menu.getItems().add(runItem);
        } else if (node.kind() == Kind.PACKAGE) {
            MenuItem runItem = new MenuItem("Run Package");
            runItem.setOnAction(e -> GuiRunner.getInstance().runNode(node));
            menu.getItems().add(runItem);

            MenuItem expandAll = new MenuItem("Expand All");
            expandAll.setOnAction(e -> expandAll(node));
            menu.getItems().add(expandAll);

            MenuItem collapseAll = new MenuItem("Collapse All");
            collapseAll.setOnAction(e -> collapseAll(node));
            menu.getItems().add(collapseAll);
        } else if (node.kind() == Kind.ROOT) {
            MenuItem runAll = new MenuItem("Run All");
            runAll.setOnAction(e -> GuiRunner.getInstance().runNode(node));
            menu.getItems().add(runAll);
        }

        return menu;
    }

    private void expandAll(DiscoveryNode node) {
        expandCollapseAll(node, true);
    }

    private void collapseAll(DiscoveryNode node) {
        expandCollapseAll(node, false);
    }

    private void expandCollapseAll(DiscoveryNode node, boolean expand) {
        TreeItem<DiscoveryNode> treeItem = node.treeItem();
        if (treeItem != null) {
            treeItem.setExpanded(expand);
            for (TreeItem<DiscoveryNode> child : treeItem.getChildren()) {
                expandCollapseRecursive(child, expand);
            }
        }
    }

    private void expandCollapseRecursive(TreeItem<DiscoveryNode> treeItem, boolean expand) {
        treeItem.setExpanded(expand);
        for (TreeItem<DiscoveryNode> child : treeItem.getChildren()) {
            expandCollapseRecursive(child, expand);
        }
    }

    private static String iconFor(DiscoveryNode.Status status) {
        return switch (status) {
            case PASSED -> ICON_SUCCESS;
            case FAILED -> ICON_FAILED;
            case SKIPPED -> ICON_SKIPPED;
            case RUNNING -> ICON_RUNNING;
            case NOT_RUN -> ICON_NOT_RUN;
        };
    }

    private static String statusStyleClass(DiscoveryNode.Status status) {
        return switch (status) {
            case PASSED -> "passed";
            case FAILED -> "failed";
            case SKIPPED -> "skipped";
            case RUNNING -> "running";
            case NOT_RUN -> "not-run";
        };
    }
}
