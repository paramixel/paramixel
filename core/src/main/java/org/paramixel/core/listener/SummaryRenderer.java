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

import org.paramixel.core.Action;
import org.paramixel.core.Runner;

/**
 * Strategy interface for rendering execution summaries.
 *
 * <p>SummaryRenderer implementations define how execution summaries are formatted
 * and displayed. Different implementations provide different visual formats:
 * table, tree, etc.</p>
 *
 * @see TableSummaryRenderer
 * @see TreeSummaryRenderer
 * @see SummaryListener
 */
interface SummaryRenderer {

    /**
     * Renders the execution summary.
     *
     * @param runner the runner that executed the action
     * @param action the root action to render
     */
    void renderSummary(Runner runner, Action action);
}
