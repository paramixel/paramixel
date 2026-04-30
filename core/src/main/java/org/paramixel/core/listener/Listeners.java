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

import org.paramixel.core.Listener;
import org.paramixel.core.internal.util.AnsiColor;

/**
 * Factory methods for creating standard listener implementations.
 *
 * <p>This class provides convenient factory methods for creating common listener
 * combinations used in typical Paramixel workflows.</p>
 *
 * <h3>Available Listeners</h3>
 * <ul>
 *   <li>{@link #defaultListener()} - Status logging with table-formatted summary</li>
 *   <li>{@link #treeListener()} - Status logging with tree-formatted summary</li>
 * </ul>
 *
 * <h3>Listener Composition</h3>
 * <p>Standard listeners are composed of:
 * <ul>
 *   <li>{@link StatusListener} - Real-time status logging for each action</li>
 *   <li>{@link SummaryListener} - Post-execution summary generation</li>
 *   <li>{@link TableSummaryRenderer} or {@link TreeSummaryRenderer} - Summary format</li>
 * </ul>
 *
 * @see Listener
 * @see StatusListener
 * @see SummaryListener
 * @see TableSummaryRenderer
 * @see TreeSummaryRenderer
 */
public final class Listeners {

    static final String PARAMIXEL = "[" + AnsiColor.BOLD_BLUE_TEXT.format("PARAMIXEL") + "] ";

    private Listeners() {}

    /**
     * Creates the default execution listener.
     *
     * <p>This listener combines real-time status logging with a table-formatted
     * execution summary. It provides a comprehensive view of action execution
     * suitable for most console-based applications.</p>
     *
     * <h3>Components</h3>
     * <ul>
     *   <li>{@link StatusListener} - Logs each action's status as it executes</li>
     *   <li>{@link SummaryListener} - Generates a table-formatted summary</li>
     *   <li>{@link TableSummaryRenderer} - Formats summary as a table</li>
     * </ul>
     *
     * <h3>Output Format</h3>
     * <p>The default listener produces:
     * <ul>
     *   <li>Real-time status lines for each action (TEST | actionName)</li>
     *   <li>Status updates (PASS/FAIL/SKIP) with elapsed time</li>
     *   <li>Table-formatted summary with all actions</li>
     *   <li>Final statistics (passed, failed, skipped counts)</li>
     *   <li>ANSI color coding for terminal output</li>
     * </ul>
     *
     * <h3>When to Use</h3>
     * <ul>
     *   <li>Standard console applications</li>
     *   <li>Development and debugging</li>
     *   <li>CI/CD pipelines</li>
     *   <li>Most use cases where visual output is needed</li>
     * </ul>
     *
     * @return a listener with status logging and table summary; never {@code null}
     * @see #treeListener()
     * @see StatusListener
     * @see SummaryListener
     */
    public static Listener defaultListener() {
        return new CompositeListener(new StatusListener(), new SummaryListener(new TableSummaryRenderer()));
    }

    /**
     * Creates a listener with tree-formatted summary output.
     *
     * <p>This listener combines real-time status logging with a tree-formatted
     * execution summary. The tree format visualizes the action hierarchy,
     * making it easier to understand parent-child relationships.</p>
     *
     * <h3>Components</h3>
     * <ul>
     *   <li>{@link StatusListener} - Logs each action's status as it executes</li>
     *   <li>{@link SummaryListener} - Generates a tree-formatted summary</li>
     *   <li>{@link TreeSummaryRenderer} - Formats summary as a tree</li>
     * </ul>
     *
     * <h3>Output Format</h3>
     * <p>The tree listener produces:
     * <ul>
     *   <li>Real-time status lines for each action</li>
     *   <li>Tree-formatted summary showing hierarchy</li>
     *   <li>Indentation and branch connectors (├──, └──)</li>
     *   <li>ANSI color coding for terminal output</li>
     * </ul>
     *
     * <h3>When to Use</h3>
     * <ul>
     *   <li>Complex action hierarchies</li>
     *   <li>Debugging nested actions</li>
     *   <li>Visualizing execution structure</li>
     *   <li>When parent-child relationships are important</li>
     * </ul>
     *
     * @return a listener with status logging and tree summary; never {@code null}
     * @see #defaultListener()
     * @see TreeSummaryRenderer
     */
    public static Listener treeListener() {
        return new CompositeListener(new StatusListener(), new SummaryListener(new TreeSummaryRenderer()));
    }
}
