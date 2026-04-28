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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.paramixel.core.Action;

public final class NodeLookup {

    private final ConcurrentMap<String, ExecutionNode> byId = new ConcurrentHashMap<>();

    public void register(ExecutionNode node) {
        byId.put(node.id(), node);
    }

    public void register(Action action, ExecutionNode node) {
        byId.put(action.id(), node);
    }

    public ExecutionNode actionNode(Action action) {
        return byId.get(action.id());
    }
}
