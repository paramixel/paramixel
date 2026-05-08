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

package org.paramixel.core;

import java.util.List;

/**
 * An {@link Action} that exposes a read-only child action hierarchy.
 *
 * <p>Composite actions define their own execution and skip semantics for their children. Implementations should return
 * a stable, unmodifiable view of the child actions that participate in the composite structure.
 */
public interface CompositeAction extends Action {

    /**
     * Returns the child actions owned by this action.
     *
     * @return the child actions in the order maintained by the implementation
     */
    List<Action> getChildren();
}
