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

import java.util.concurrent.CountDownLatch;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.paramixel.gui.GuiExecutionListener;

public final class ExecutionViewApp extends Application {

    private static final String ROOT_STYLE =
            "-fx-background-color: #1e1e1e; -fx-text-fill: #d4d4d4; -fx-font-family: monospace; -fx-font-size: 13px;";

    private static final String TREE_VIEW_STYLE = "-fx-background-color: #1e1e1e;"
            + " -fx-control-inner-background: #1e1e1e;"
            + " -fx-control-inner-background-alt: #252526;"
            + " -fx-border-color: transparent;"
            + " -fx-show-vert-lines: false;"
            + " -fx-show-horz-lines: false;";

    private static final String SUMMARY_STYLE = "-fx-padding: 8 12 8 12;"
            + " -fx-font-weight: bold;"
            + " -fx-text-fill: #d4d4d4;"
            + " -fx-background-color: #2d2d2d;"
            + " -fx-border-color: #3c3c3c;"
            + " -fx-border-width: 1 0 0 0;";

    private static volatile CountDownLatch readyLatchBridge;
    private static volatile CountDownLatch closedLatchBridge;
    private static volatile GuiExecutionListener listenerBridge;

    private final CountDownLatch readyLatch;
    private final CountDownLatch closedLatch;
    private final GuiExecutionListener listener;

    private TreeView<ExecutionNode> treeView;
    private Label summaryLabel;
    private Stage stage;

    public ExecutionViewApp() {
        this.readyLatch = readyLatchBridge;
        this.closedLatch = closedLatchBridge;
        this.listener = listenerBridge;
    }

    public static void configureBridge(
            CountDownLatch readyLatch, CountDownLatch closedLatch, GuiExecutionListener listener) {
        readyLatchBridge = readyLatch;
        closedLatchBridge = closedLatch;
        listenerBridge = listener;
    }

    @Override
    public void init() {
        if (listener != null) {
            listener.setApp(this);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        TreeItem<ExecutionNode> rootItem = listener.buildRootItem();
        treeView = new TreeView<>(rootItem);
        treeView.setCellFactory(tv -> new ExecutionCell());
        treeView.setShowRoot(true);
        treeView.setEditable(false);
        treeView.setStyle(TREE_VIEW_STYLE);

        summaryLabel = new Label("Waiting for execution...");
        summaryLabel.setStyle(SUMMARY_STYLE);
        summaryLabel.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        root.setStyle(ROOT_STYLE);
        root.setCenter(treeView);
        root.setBottom(summaryLabel);
        BorderPane.setMargin(summaryLabel, new Insets(0));

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = screen.getWidth() / 2;
        double height = screen.getHeight() / 2;
        Scene scene = new Scene(root, width, height);
        scene.setFill(Color.valueOf("#1e1e1e"));

        primaryStage.setTitle("Paramixel Execution Results");
        primaryStage.setScene(scene);
        primaryStage.setX((screen.getWidth() - width) / 2);
        primaryStage.setY((screen.getHeight() - height) / 2);
        primaryStage.setOnCloseRequest(event -> {
            closedLatch.countDown();
            Platform.exit();
        });
        primaryStage.show();

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(250), event -> updateSummary()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        readyLatch.countDown();
    }

    public void setRootItem(TreeItem<ExecutionNode> rootItem) {
        if (rootItem != null) {
            rootItem.setExpanded(true);
        }
        if (treeView != null) {
            treeView.setRoot(rootItem);
        }
    }

    public TreeView<ExecutionNode> treeView() {
        return treeView;
    }

    public Stage stage() {
        return stage;
    }

    private void updateSummary() {
        ExecutionNode rootNode = listener.rootNode();
        Counts counts = countDescendants(rootNode);
        String timingText = "";
        if (rootNode.timing() != null) {
            timingText = " | " + rootNode.timing().toMillis() + " ms";
        }
        summaryLabel.setText(String.format(
                "%d branches | %d leaves | \u2714 %d passed | \u2718 %d failed | \u23ED %d skipped%s",
                counts.branches, counts.leaves, counts.passed, counts.failed, counts.skipped, timingText));
    }

    private static Counts countDescendants(ExecutionNode node) {
        Counts counts = new Counts();
        countDescendants(node, counts);
        return counts;
    }

    private static void countDescendants(ExecutionNode node, Counts counts) {
        switch (node.kind()) {
            case BRANCH -> counts.branches++;
            case LEAF -> counts.leaves++;
            case EXECUTION -> {}
        }

        if (node.kind() == ExecutionNode.Kind.LEAF) {
            switch (node.status()) {
                case SUCCESSFUL -> counts.passed++;
                case FAILED -> counts.failed++;
                case SKIPPED -> counts.skipped++;
                case WAITING, RUNNING -> {}
            }
        }

        for (ExecutionNode child : node.children()) {
            countDescendants(child, counts);
        }
    }

    private static final class Counts {
        private int branches;
        private int leaves;
        private int passed;
        private int failed;
        private int skipped;
    }
}
