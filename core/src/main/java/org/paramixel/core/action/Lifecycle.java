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
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.Status;
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.spi.DefaultStatus;
import org.paramixel.core.support.Arguments;

/**
 * Executes a before/primary/after lifecycle sequence.
 *
 * <p>The {@code before} action runs first. If it fails or skips, the {@code primary} action is skipped recursively.
 * The {@code after} action still runs, allowing follow-up behavior to occur even when earlier actions fail.
 */
public class Lifecycle extends BranchAction {

    private final Action before;
    private final Action primary;
    private final Action after;

    /**
     * Creates a lifecycle action from explicit before, primary, and after actions.
     *
     * @param name the action name
     * @param before the first action
     * @param primary the primary action
     * @param after the final action
     */
    protected Lifecycle(String name, Action before, Action primary, Action after) {
        super(List.of(before, primary, after));
        this.name = validateName(name);
        this.before = before;
        this.primary = primary;
        this.after = after;
    }

    /**
     * Creates a lifecycle action.
     *
     * @param name the action name
     * @param before the first action
     * @param primary the primary action
     * @param after the final action
     * @return a new lifecycle action
     */
    public static Lifecycle of(String name, Action before, Action primary, Action after) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(before, "before must not be null");
        Objects.requireNonNull(primary, "primary must not be null");
        Objects.requireNonNull(after, "after must not be null");
        Lifecycle instance = new Lifecycle(name, before, primary, after);
        instance.initialize();
        return instance;
    }

    /**
     * Executes the lifecycle sequence.
     *
     * @param context the execution context
     * @return the aggregated lifecycle result
     */
    @Override
    public Result execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        DefaultResult result = new DefaultResult(this);
        context.getListener().beforeAction(result);
        Instant start = Instant.now();

        Status computedStatus = DefaultStatus.PASS;

        Context lifecycleContext = context.createChild();
        Result beforeResult = before.execute(lifecycleContext);
        result.addChild(beforeResult);
        if (beforeResult.getStatus().isSkip()) {
            computedStatus = new DefaultStatus(
                    DefaultStatus.Kind.SKIP,
                    beforeResult.getStatus().getMessage().orElse(null));
        } else if (beforeResult.getStatus().isFailure()) {
            computedStatus = new DefaultStatus(
                    DefaultStatus.Kind.FAILURE,
                    beforeResult.getStatus().getMessage().orElse(null));
        }

        if (computedStatus.isPass()) {
            Result primaryResult = primary.execute(lifecycleContext.createChild());
            result.addChild(primaryResult);
            if (primaryResult.getStatus().isFailure()) {
                String primaryFailureMessage =
                        primaryResult.getStatus().getMessage().orElse(null);
                computedStatus = new DefaultStatus(DefaultStatus.Kind.FAILURE, primaryFailureMessage);
            } else if (primaryResult.getStatus().isSkip()) {
                computedStatus = new DefaultStatus(
                        DefaultStatus.Kind.SKIP,
                        primaryResult.getStatus().getMessage().orElse(null));
            }
        } else {
            Result skipResult = skipWithDescendants(primary, lifecycleContext.createChild());
            result.addChild(skipResult);
        }

        Result afterResult = after.execute(lifecycleContext);
        result.addChild(afterResult);
        if (afterResult.getStatus().isFailure()) {
            String afterFailureMessage = afterResult.getStatus().getMessage().orElse(null);
            if (!computedStatus.isFailure() || computedStatus.getMessage().isEmpty()) {
                computedStatus = new DefaultStatus(DefaultStatus.Kind.FAILURE, afterFailureMessage);
            }
        }

        result.setStatus(computedStatus);
        result.setElapsedTime(Duration.between(start, Instant.now()));
        context.getListener().afterAction(result);
        return result;
    }

    /**
     * Skips the lifecycle sequence.
     *
     * @param context the execution context
     * @return the skipped lifecycle result
     */
    @Override
    public Result skip(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        DefaultResult result = new DefaultResult(this);
        Context lifecycleContext = context.createChild();
        result.addChild(before.skip(lifecycleContext));
        result.addChild(primary.skip(lifecycleContext.createChild()));
        result.addChild(after.skip(lifecycleContext));
        result.setStatus(DefaultStatus.SKIP);
        result.setElapsedTime(Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }

    private static Result skipWithDescendants(Action action, Context context) {
        DefaultResult result = new DefaultResult(action);
        for (Action child : action.getChildren()) {
            Result childResult = skipWithDescendants(child, context.createChild());
            result.addChild(childResult);
        }
        result.setStatus(DefaultStatus.SKIP);
        result.setElapsedTime(Duration.ZERO);
        context.getListener().skipAction(result);
        return result;
    }
}
