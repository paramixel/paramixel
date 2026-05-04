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

package org.paramixel.core.spi.listener;

import org.paramixel.core.Result;
import org.paramixel.core.Runner;

/**
 * Renders a completed run summary for console-oriented listeners.
 */
public interface SummaryRenderer {

    /**
     * Renders a summary for the completed run and root result.
     *
     * @param runner the runner that executed the action tree
     * @param result the completed root result
     */
    void renderSummary(Runner runner, Result result);
}
