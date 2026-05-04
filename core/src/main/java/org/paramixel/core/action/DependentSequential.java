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
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.support.Arguments;

/**
 * Executes child actions sequentially in declaration order, where each child depends on the successful completion
 * of the children before it.
 *
 * <p>When a child fails, execution stops immediately and all remaining children are marked as skipped instead of
 * executed.
 */
public class DependentSequential extends BranchAction {

    /**
     * Creates a dependent sequential action with the supplied children.
     *
     * @param name the action name
     * @param children the child actions
     */
    protected DependentSequential(String name, List<Action> children) {
        super(children);
        this.name = validateName(name);
    }

    /**
     * Creates a dependent sequential action.
     *
     * @param name the action name
     * @param children the child actions
     * @return a new dependent sequential action
     */
    public static DependentSequential of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        DependentSequential instance = new DependentSequential(name, children);
        instance.initialize();
        return instance;
    }

    /**
     * Creates a dependent sequential action.
     *
     * @param name the action name
     * @param children the child actions
     * @return a new dependent sequential action
     */
    public static DependentSequential of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        DependentSequential instance = new DependentSequential(name, List.of(children));
        instance.initialize();
        return instance;
    }

    /**
     * Executes children in order until one fails.
     *
     * <p>Each child depends on the successful completion of every earlier child. After the first failure, all
     * remaining children are skipped.
     *
     * @param context the execution context
     * @return the aggregated execution result
     */
    @Override
    public Result execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        DefaultResult result = new DefaultResult(this);
        context.getListener().beforeAction(result);
        Instant start = Instant.now();
        for (Action child : getChildren()) {
            Result childResult = child.execute(context.createChild());
            result.addChild(childResult);
            if (childResult.getStatus().isFailure()) {
                for (Action remaining : getChildren()
                        .subList(getChildren().indexOf(child) + 1, getChildren().size())) {
                    Result skipResult = remaining.skip(context.createChild());
                    result.addChild(skipResult);
                }
                break;
            }
        }
        result.setStatus(computeStatus(result.getChildren()));
        result.setElapsedTime(Duration.between(start, Instant.now()));
        context.getListener().afterAction(result);
        return result;
    }
}
