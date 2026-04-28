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

public final class Listeners {

    static final String PARAMIXEL = "[" + AnsiColor.BOLD_BLUE_TEXT.format("PARAMIXEL") + "] ";

    private Listeners() {}

    public static Listener defaultListener() {
        return new CompositeListener(new StatusListener(), new SummaryListener(new TableSummaryRenderer()));
    }

    public static Listener treeListener() {
        return new CompositeListener(new StatusListener(), new SummaryListener(new TreeSummaryRenderer()));
    }
}
