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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.gui.internal.ExecutionNode;
import org.paramixel.gui.internal.ExecutionNode.Kind;
import org.paramixel.gui.internal.ExecutionNode.Status;
import org.paramixel.gui.internal.ExecutionViewApp;
import org.paramixel.gui.internal.NodeLookup;

/**
 * A {@link Listener} that updates a JavaFX GUI during execution.
 */
public class GuiExecutionListener implements Listener {

    private static final String EXECUTION_ID = "__execution__";

    private final NodeLookup nodeLookup = new NodeLookup();
    private final CountDownLatch fxReadyLatch = new CountDownLatch(1);
    private final CountDownLatch windowClosedLatch = new CountDownLatch(1);

    private final ExecutionNode rootNode;
    private final boolean uiEnabled;
    private volatile ExecutionViewApp app;

    /**
     * Creates a GUI listener and launches the UI.
     *
     * @param rootAction The root action to visualize; must not be null.
     */
    public GuiExecutionListener(Action rootAction) {
        this(rootAction, true);
    }

    /**
     * Creates a GUI listener, optionally launching the UI.
     *
     * @param rootAction The root action to visualize; must not be null.
     * @param launchUi Whether to launch the JavaFX UI.
     */
    GuiExecutionListener(Action rootAction, boolean launchUi) {
        Objects.requireNonNull(rootAction, "rootAction must not be null");
        this.rootNode = createExecutionTree(rootAction);
        this.uiEnabled = launchUi;

        if (!launchUi) {
            fxReadyLatch.countDown();
            windowClosedLatch.countDown();
            return;
        }

        ExecutionViewApp.configureBridge(fxReadyLatch, windowClosedLatch, this);

        Thread fxThread = new Thread(
                () -> {
                    try {
                        Application.launch(ExecutionViewApp.class, new String[0]);
                    } catch (IllegalStateException e) {
                        fxReadyLatch.countDown();
                        windowClosedLatch.countDown();
                    }
                },
                "paramixel-javafx");
        fxThread.setDaemon(true);
        fxThread.start();

        try {
            fxReadyLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the root execution node.
     *
     * @return The root node.
     */
    public ExecutionNode rootNode() {
        return rootNode;
    }

    /**
     * Sets the JavaFX application instance.
     *
     * @param app The application.
     */
    public void setApp(ExecutionViewApp app) {
        this.app = app;
    }

    /**
     * Builds the root tree item for the GUI.
     *
     * @return The root tree item.
     */
    public TreeItem<ExecutionNode> buildRootItem() {
        return createTreeItem(rootNode, 0);
    }

    /**
     * Waits for the GUI window to close.
     *
     * @throws InterruptedException If the thread is interrupted while waiting.
     */
    public void join() throws InterruptedException {
        windowClosedLatch.await();
    }

    @Override
    public void beforeAction(Context context, Action action) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(action, "action must not be null");

        rootNode.setStatus(Status.RUNNING);
        refreshNode(rootNode);

        ExecutionNode actionNode = nodeLookup.actionNode(context.action());
        if (actionNode == null) {
            return;
        }

        actionNode.markRunning();
        refreshLineage(actionNode);
    }

    @Override
    public void afterAction(Context context, Action action, Result result) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(result, "result must not be null");

        ExecutionNode actionNode = nodeLookup.actionNode(context.action());
        if (actionNode == null) {
            return;
        }

        Status status =
                switch (result.status()) {
                    case PASS -> Status.SUCCESSFUL;
                    case FAIL -> Status.FAILED;
                    case SKIP -> Status.SKIPPED;
                };

        Throwable throwable = result.failure().orElse(null);
        String skipReason = status == Status.SKIPPED ? "Action skipped" : null;
        Duration timing = result.timing();
        actionNode.markCompleted(status, throwable, skipReason, timing);

        if (context.parent().isEmpty()) {
            rootNode.markCompleted(actionNode.status(), throwable, skipReason, timing);
        }

        refreshLineage(actionNode);
        refreshNode(rootNode);
    }

    private ExecutionNode createExecutionTree(Action rootAction) {
        ExecutionNode executionNode = new ExecutionNode(EXECUTION_ID, Kind.EXECUTION, "Execution");
        nodeLookup.register(executionNode);

        ExecutionNode rootActionNode = createActionNode(rootAction);
        rootActionNode.setParent(executionNode);

        return executionNode;
    }

    private ExecutionNode createActionNode(Action action) {
        ExecutionNode existing = nodeLookup.actionNode(action);
        if (existing != null) {
            return existing;
        }

        Kind kind = action.children().isEmpty() ? Kind.LEAF : Kind.BRANCH;
        ExecutionNode node = new ExecutionNode(action.id(), kind, action.name());
        nodeLookup.register(action, node);

        for (Action child : action.children()) {
            createActionNode(child).setParent(node);
        }

        return node;
    }

    private TreeItem<ExecutionNode> createTreeItem(ExecutionNode node, int depth) {
        TreeItem<ExecutionNode> item = new TreeItem<>(node);
        item.setExpanded(depth <= 1);
        node.setTreeItem(item);
        for (ExecutionNode child : node.children()) {
            item.getChildren().add(createTreeItem(child, depth + 1));
        }
        return item;
    }

    private void refreshLineage(ExecutionNode node) {
        ExecutionNode current = node;
        while (current != null) {
            refreshNode(current);
            current = current.parent();
        }
    }

    private void refreshNode(ExecutionNode node) {
        if (!uiEnabled) {
            return;
        }

        runOnFxThread(() -> {
            TreeItem<ExecutionNode> item = node.treeItem();
            if (item == null) {
                if (app != null && app.treeView() != null && app.treeView().getRoot() == null && node == rootNode) {
                    app.setRootItem(buildRootItem());
                }
                return;
            }
            ExecutionNode currentValue = item.getValue();
            item.setValue(null);
            item.setValue(currentValue);
        });
    }

    private void runOnFxThread(Runnable action) {
        if (!uiEnabled) {
            action.run();
            return;
        }

        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
