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
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

public class SummaryListener implements Listener {

    private static final String PARAMIXEL = Listeners.PARAMIXEL;
    private final SummaryRenderer summaryRenderer;

    SummaryListener(SummaryRenderer summaryRenderer) {
        this.summaryRenderer = summaryRenderer;
    }

    @Override
    public void planStarted(Runner runner, Action action) {
        System.out.println(PARAMIXEL + "Started " + action.name() + " ...");
    }

    @Override
    public void planCompleted(Runner runner, Result result) {
        System.out.println(PARAMIXEL + result.action().name() + " completed");
        summaryRenderer.renderSummary(runner, result);
    }
}
