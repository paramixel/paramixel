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

import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;

public class CompositeListener implements Listener {

    private final Listener listener1;
    private final Listener listener2;

    public CompositeListener(Listener listener1, Listener listener2) {
        this.listener1 = Objects.requireNonNull(listener1, "listener1 must not be null");
        this.listener2 = Objects.requireNonNull(listener2, "listener2 must not be null");
    }

    @Override
    public void planStarted(Runner runner, Action action) {
        listener1.planStarted(runner, action);
        listener2.planStarted(runner, action);
    }

    @Override
    public void planCompleted(Runner runner, Result result) {
        listener1.planCompleted(runner, result);
        listener2.planCompleted(runner, result);
    }

    @Override
    public void beforeAction(Context context, Action action) {
        listener1.beforeAction(context, action);
        listener2.beforeAction(context, action);
    }

    @Override
    public void afterAction(Context context, Action action, Result result) {
        listener1.afterAction(context, action, result);
        listener2.afterAction(context, action, result);
    }
}
