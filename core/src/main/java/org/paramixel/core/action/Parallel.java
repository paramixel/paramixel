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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;

/**
 * An {@link AbstractAction} that executes child actions concurrently.
 */
public class Parallel extends AbstractAction {

    private final int parallelism;
    private final List<Action> children;

    private Parallel(String name, int parallelism, List<Action> children) {
        super(name);
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive, was: " + parallelism);
        }
        Objects.requireNonNull(children, "children must not be null");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("parallel action must have at least one child");
        }
        List<Action> validated = new ArrayList<>(children.size());
        for (Action child : children) {
            validated.add(Objects.requireNonNull(child, "children must not contain null elements"));
        }
        this.parallelism = parallelism;
        this.children = Collections.unmodifiableList(validated);
        this.children.forEach(this::adopt);
    }

    /**
     * Creates a parallel action with unbounded parallelism.
     *
     * @param name The action name; must not be null.
     * @param children The child actions; must not be null or empty.
     * @return A new Parallel action.
     */
    public static Parallel of(String name, List<Action> children) {
        return new Parallel(name, Integer.MAX_VALUE, children);
    }

    /**
     * Creates a parallel action with a specific parallelism level.
     *
     * @param name The action name; must not be null.
     * @param parallelism The maximum number of concurrent actions; must be positive.
     * @param children The child actions; must not be null or empty.
     * @return A new Parallel action.
     */
    public static Parallel of(String name, int parallelism, List<Action> children) {
        return new Parallel(name, parallelism, children);
    }

    /**
     * Creates a parallel action with unbounded parallelism.
     *
     * @param name The action name; must not be null.
     * @param children The child actions; must not be null or empty.
     * @return A new Parallel action.
     */
    public static Parallel of(String name, Action... children) {
        if (children.length == 0) {
            throw new IllegalArgumentException("children must not be empty");
        }
        return new Parallel(name, Integer.MAX_VALUE, List.of(children));
    }

    /**
     * Creates a parallel action with a specific parallelism level.
     *
     * @param name The action name; must not be null.
     * @param parallelism The maximum number of concurrent actions; must be positive.
     * @param children The child actions; must not be null or empty.
     * @return A new Parallel action.
     */
    public static Parallel of(String name, int parallelism, Action... children) {
        if (children.length == 0) {
            throw new IllegalArgumentException("children must not be empty");
        }
        return new Parallel(name, parallelism, List.of(children));
    }

    /**
     * Returns the parallelism level.
     *
     * @return The maximum number of concurrent actions.
     */
    public int parallelism() {
        return parallelism;
    }

    @Override
    public List<Action> children() {
        return children;
    }

    @Override
    protected Result doExecute(Context context, Instant start) throws Throwable {
        List<CompletableFuture<Result>> futures = new ArrayList<>();
        Semaphore semaphore = new Semaphore(parallelism);
        for (Action child : children()) {
            semaphore.acquire();
            futures.add(context.executeAsync(child).whenComplete((r, t) -> semaphore.release()));
        }
        List<Result> childResults =
                futures.stream().map(CompletableFuture::join).toList();
        return Result.of(
                this, computeStatus(childResults), durationSince(start), findFailure(childResults), childResults);
    }
}
