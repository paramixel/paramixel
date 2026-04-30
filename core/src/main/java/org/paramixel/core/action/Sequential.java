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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.internal.DefaultContext;
import org.paramixel.core.internal.Results;
import org.paramixel.core.internal.util.Arguments;

/**
 * A built-in action that executes child actions sequentially.
 */
public class Sequential extends AbstractAction {

    private final List<Action> children;

    protected Sequential(String name, List<Action> children) {
        super(name);
        this.children = validateChildren(children);
    }

    /**
     * Creates a sequential action.
     *
     * @param name the action name; must not be {@code null}
     * @param children the child actions; must not be {@code null} or empty
     * @return a new sequential action
     */
    public static Sequential of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        return new Sequential(name, children);
    }

    /**
     * Creates a sequential action from varargs children.
     *
     * @param name the action name; must not be {@code null}
     * @param children the child actions; must not be {@code null} or empty
     * @return a new sequential action
     */
    public static Sequential of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNotBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        return new Sequential(name, List.of(children));
    }

    /**
     * Returns the child actions executed by this sequential action.
     *
     * <p>The returned list is unmodifiable and reflects the execution order established
     * at construction time.</p>
     *
     * @return the child actions
     */
    @Override
    public List<Action> getChildren() {
        return children;
    }

    @Override
    public void execute(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        this.result = Results.staged();
        context.getListener().beforeAction(context, this);
        Instant start = Instant.now();
        DefaultContext defaultContext = (DefaultContext) context;
        for (Action child : getChildren()) {
            child.execute(new DefaultContext(defaultContext));
        }
        this.result = Results.of(computeStatus(), durationSince(start));
        context.getListener().afterAction(context, this, this.result);
    }
}
