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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;

/**
 * Base implementation for actions that own child actions.
 *
 * <p>This class validates child collections, provides default skip propagation, and computes aggregate status from
 * child results.
 *
 * @implSpec Aggregate status resolution prefers failure over skip, and skip over pass.
 */
public abstract class BranchAction extends AbstractAction {

    protected final List<Action> children;

    /**
     * Creates a branch action with the supplied child actions.
     *
     * @param children the child actions owned by this branch
     */
    protected BranchAction(List<Action> children) {
        this.children = validateChildren(children);
    }

    /**
     * Returns the immutable child action list.
     *
     * @return the child actions
     */
    @Override
    public List<Action> getChildren() {
        return children;
    }

    /**
     * Produces a skipped result for this action and all descendants.
     *
     * @param context the execution context
     * @return the skipped result tree
     */
    @Override
    public Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        DefaultResult result = new DefaultResult(this);
        for (Action child : children) {
            Context childContext = context.createChild();
            Result childResult = child.skip(childContext);
            result.addChild(childResult);
        }
        result.setStatus(DefaultStatus.SKIP);
        result.setElapsedTime(Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }

    /**
     * Computes the aggregate status for the supplied child results.
     *
     * @param childResults the child results to evaluate
     * @return {@link DefaultStatus#FAILURE}, {@link DefaultStatus#SKIP}, or {@link DefaultStatus#PASS}
     */
    protected Status computeStatus(List<Result> childResults) {
        for (Result childResult : childResults) {
            Objects.requireNonNull(childResult, "childResults must not contain null elements");
            if (childResult.getStatus().isFailure()) {
                return DefaultStatus.FAILURE;
            }
        }
        for (Result childResult : childResults) {
            if (childResult.getStatus().isSkip()) {
                return DefaultStatus.SKIP;
            }
        }
        return DefaultStatus.PASS;
    }

    /**
     * Validates, parents, and stores child actions.
     *
     * @param children the proposed child actions
     * @return an immutable validated child list
     * @throws NullPointerException if {@code children} or any child is {@code null}
     * @throws IllegalArgumentException if {@code children} is empty
     */
    protected List<Action> validateChildren(List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("children must not be empty");
        }
        List<Action> validated = new ArrayList<>(children.size());
        for (Action child : children) {
            Objects.requireNonNull(child, "children must not contain null elements");
            addChild(child);
            validated.add(child);
        }
        return Collections.unmodifiableList(validated);
    }
}
