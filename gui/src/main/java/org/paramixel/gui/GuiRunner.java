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

/**
 * Placeholder for the Test Explorer GUI application.
 *
 * <p>This class is referenced by {@link DiscoveryCell} for running nodes
 * from the context menu. The full Test Explorer implementation is a work
 * in progress.</p>
 */
public final class GuiRunner {

    private static GuiRunner instance;

    private GuiRunner() {}

    /**
     * Returns the singleton instance.
     *
     * @return the singleton instance
     */
    public static GuiRunner getInstance() {
        if (instance == null) {
            instance = new GuiRunner();
        }
        return instance;
    }

    /**
     * Runs the selected node.
     *
     * @param node the node to run
     */
    public void runNode(DiscoveryNode node) {
        if (node.action() != null) {
            GuiExecutionListener listener = new GuiExecutionListener(node.action());
            try {
                org.paramixel.core.Runner.builder().listener(listener).build().run(node.action());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
