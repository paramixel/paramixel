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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;

/**
 * An {@link AbstractAction} that executes child actions sequentially.
 */
public class Sequential extends AbstractAction {

    private final List<Action> children;

    protected Sequential(String name, List<Action> children) {
        super(name);
        Objects.requireNonNull(children, "children must not be null");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("sequential action must have at least one child");
        }
        List<Action> validated = new ArrayList<>(children.size());
        for (Action child : children) {
            validated.add(Objects.requireNonNull(child, "children must not contain null elements"));
        }
        this.children = Collections.unmodifiableList(validated);
        this.children.forEach(this::adopt);
    }

    /**
     * Creates a sequential action.
     * @param name The action name; must not be null.
     * @param children The child actions; must not be null or empty.
     * @return new Sequential action.
     */
    public static Sequential of(String name, List<Action> children) {
        return new Sequential(name, children);
    }

    /**
     * Creates a sequential action.
     * @param name The action name; must not be null.
     * @param children The child actions; must not be null or empty.
     * @return new Sequential action.
     */
    public static Sequential of(String name, Action... children) {
        Objects.requireNonNull(children, "children must not be null");
        return new Sequential(name, List.of(children));
    }

    @Override
    public List<Action> children() {
        return children;
    }

    @Override
    protected Result doExecute(Context context, Instant start) throws Throwable {
        List<Result> childResults = new ArrayList<>();
        for (Action child : children()) {
            childResults.add(context.execute(child));
        }
        return Result.of(
                this, computeStatus(childResults), durationSince(start), findFailure(childResults), childResults);
    }
}
