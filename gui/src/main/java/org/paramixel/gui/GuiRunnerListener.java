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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.gui.DiscoveryNode.Status;

public final class GuiRunnerListener implements Listener {

    private final Map<String, DiscoveryNode> nodeLookup;
    private final Runnable refreshCallback;

    public GuiRunnerListener(Map<String, DiscoveryNode> nodeLookup, Runnable refreshCallback) {
        this.nodeLookup = new ConcurrentHashMap<>(nodeLookup);
        this.refreshCallback = refreshCallback;
    }

    @Override
    public void beforeAction(Context context, Action action) {
        DiscoveryNode node = nodeLookup.get(action.id());
        if (node != null) {
            runOnFxThread(() -> {
                node.markRunning();
                triggerRefresh();
            });
        }
    }

    @Override
    public void afterAction(Context context, Action action, Result result) {
        DiscoveryNode node = nodeLookup.get(action.id());
        if (node != null) {
            Status status =
                    switch (result.status()) {
                        case PASS -> Status.PASSED;
                        case FAIL -> Status.FAILED;
                        case SKIP -> Status.SKIPPED;
                    };

            Throwable throwable = result.failure().orElse(null);
            String skipReason = status == Status.SKIPPED ? "Action skipped" : null;

            runOnFxThread(() -> {
                node.markCompleted(status, throwable);
                triggerRefresh();
            });
        }
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void triggerRefresh() {
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }
}
