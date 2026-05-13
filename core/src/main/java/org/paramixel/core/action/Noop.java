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

package org.paramixel.core.action;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.internal.DefaultResult;
import org.paramixel.core.internal.DefaultStatus;

/**
 * An action that always passes and performs no work. Useful as a placeholder or fixture in action hierarchies.
 */
public final class Noop extends AbstractAction {

    private Noop(String name) {
        this.name = validateName(name);
    }

    /**
     * Creates a no-op action.
     *
     * @param name the action name
     * @return a new no-op action
     */
    public static Noop of(String name) {
        var instance = new Noop(name);
        instance.initialize();
        return instance;
    }

    @Override
    public Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        var result = new DefaultResult(this);
        result.complete(DefaultStatus.SKIP, Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }

    /**
     * Produces a passing result without invoking any user code.
     *
     * @param context the run context
     * @return the run result
     */
    @Override
    public Result run(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        var result = new DefaultResult(this);
        var listener = context.getListener();
        listener.beforeAction(result);
        Instant start = Instant.now();
        result.complete(DefaultStatus.PASS, Duration.between(start, Instant.now()));
        listener.afterAction(result);
        return result;
    }
}
