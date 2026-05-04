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
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;
import org.paramixel.core.support.Arguments;

/**
 * A leaf action that always passes and performs no work.
 */
public final class Noop extends LeafAction {

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
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Noop instance = new Noop(name);
        instance.initialize();
        return instance;
    }

    /**
     * Produces a passing result without invoking any user code.
     *
     * @param context the execution context
     * @return the execution result
     */
    @Override
    public Result execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        DefaultResult result = new DefaultResult(this);
        context.getListener().beforeAction(result);
        Instant start = Instant.now();
        result.setStatus(DefaultStatus.PASS);
        result.setElapsedTime(Duration.between(start, Instant.now()));
        context.getListener().afterAction(result);
        return result;
    }
}
