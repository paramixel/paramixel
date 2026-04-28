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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.paramixel.core.Action;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;

public final class ExecutionService {

    public enum Composition {
        SEQUENTIAL,
        PARALLEL
    }

    private ExecutionService() {}

    public static CompletableFuture<Result> execute(
            DiscoveryNode node, Composition composition, Listener listener, Consumer<Result> callback) {

        List<Action> actions = collectActions(node);
        if (actions.isEmpty()) {
            CompletableFuture<Result> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("No actions to execute"));
            return future;
        }

        Action rootAction = composeActions(actions, composition);

        return CompletableFuture.supplyAsync(() -> {
            Runner runner = Runner.builder().listener(listener).build();
            Result result = runner.run(rootAction);
            if (callback != null) {
                callback.accept(result);
            }
            return result;
        });
    }

    public static Map<String, DiscoveryNode> buildNodeLookup(DiscoveryNode node) {
        Map<String, DiscoveryNode> lookup = new HashMap<>();
        buildNodeLookupRecursive(node, lookup);
        return lookup;
    }

    private static List<Action> collectActions(DiscoveryNode node) {
        List<Action> actions = new ArrayList<>();
        collectActionsRecursive(node, actions);
        return actions;
    }

    private static void collectActionsRecursive(DiscoveryNode node, List<Action> actions) {
        if (node.kind() == DiscoveryNode.Kind.ACTION && node.action() != null) {
            actions.add(node.action());
        }
        for (DiscoveryNode child : node.children()) {
            collectActionsRecursive(child, actions);
        }
    }

    private static Action composeActions(List<Action> actions, Composition composition) {
        if (actions.size() == 1) {
            return actions.get(0);
        }

        return switch (composition) {
            case SEQUENTIAL -> Sequential.of("plan", actions);
            case PARALLEL -> Parallel.of("plan", Runtime.getRuntime().availableProcessors(), actions);
        };
    }

    private static void buildNodeLookupRecursive(DiscoveryNode node, Map<String, DiscoveryNode> lookup) {
        if (node.action() != null) {
            lookup.put(node.action().id(), node);
        }
        for (DiscoveryNode child : node.children()) {
            buildNodeLookupRecursive(child, lookup);
        }
    }
}
