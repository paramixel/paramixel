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
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;

/**
 * Base implementation for actions that cannot have children.
 */
public abstract class LeafAction extends AbstractAction {

    /**
     * Returns an empty child list.
     *
     * @return an empty list
     */
    @Override
    public final List<Action> getChildren() {
        return List.of();
    }

    /**
     * Always rejects child attachment because leaf actions cannot own descendants.
     *
     * @param child the attempted child action
     * @throws UnsupportedOperationException always
     */
    @Override
    public void addChild(Action child) {
        throw new UnsupportedOperationException("leaf action cannot have children");
    }

    /**
     * Produces a skipped result for this leaf action.
     *
     * @param context the execution context
     * @return the skipped result
     */
    @Override
    public Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        DefaultResult result = new DefaultResult(this);
        result.setStatus(DefaultStatus.SKIP);
        result.setElapsedTime(Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }
}
