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
 * Executes child actions sequentially in declaration order.
 *
 * <p>All children are executed regardless of earlier failures. Aggregate status is computed from the full child result
 * list.
 */
public class Sequential extends BranchAction {

    /**
     * Creates a sequential action with the supplied children.
     *
     * @param name the action name
     * @param children the child actions
     */
    protected Sequential(String name, List<Action> children) {
        super(children);
        this.name = validateName(name);
    }

    /**
     * Creates a sequential action.
     *
     * @param name the action name
     * @param children the child actions
     * @return a new sequential action
     */
    public static Sequential of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        Sequential instance = new Sequential(name, children);
        instance.initialize();
        return instance;
    }

    /**
     * Creates a sequential action.
     *
     * @param name the action name
     * @param children the child actions
     * @return a new sequential action
     */
    public static Sequential of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        Sequential instance = new Sequential(name, List.of(children));
        instance.initialize();
        return instance;
    }

    /**
     * Executes all children in order.
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
        }
        result.setStatus(computeStatus(result.getChildren()));
        result.setElapsedTime(Duration.between(start, Instant.now()));
        context.getListener().afterAction(result);
        return result;
    }
}
