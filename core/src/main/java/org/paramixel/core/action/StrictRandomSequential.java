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
import java.util.Random;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;

/**
 * A {@link RandomSequential} action that stops execution on first failure.
 * Remaining children are reported as skipped with listener callbacks.
 */
public class StrictRandomSequential extends RandomSequential {

    private StrictRandomSequential(String name, List<Action> children, Long seed) {
        super(name, children, seed);
    }

    /**
     * Creates a strict random sequential action with no seed (non-deterministic order).
     *
     * @param name The action name; must not be null.
     * @param children The child actions; must not be null or empty.
     * @return A new StrictRandomSequential action.
     */
    public static StrictRandomSequential of(String name, List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        return new StrictRandomSequential(name, children, null);
    }

    /**
     * Creates a strict random sequential action with no seed (non-deterministic order).
     *
     * @param name The action name; must not be null.
     * @param children The child actions; must not be null or empty.
     * @return A new StrictRandomSequential action.
     */
    public static StrictRandomSequential of(String name, Action... children) {
        if (children.length == 0) {
            throw new IllegalArgumentException("children must not be empty");
        }
        return new StrictRandomSequential(name, List.of(children), null);
    }

    /**
     * Creates a strict random sequential action with a seed for reproducibility.
     *
     * @param name The action name; must not be null.
     * @param seed The random seed for reproducible ordering.
     * @param children The child actions; must not be null or empty.
     * @return A new StrictRandomSequential action.
     */
    public static StrictRandomSequential of(String name, long seed, List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        return new StrictRandomSequential(name, children, seed);
    }

    /**
     * Creates a strict random sequential action with a seed for reproducibility.
     *
     * @param name The action name; must not be null.
     * @param seed The random seed for reproducible ordering.
     * @param children The child actions; must not be null or empty.
     * @return A new StrictRandomSequential action.
     */
    public static StrictRandomSequential of(String name, long seed, Action... children) {
        if (children.length == 0) {
            throw new IllegalArgumentException("children must not be empty");
        }
        return new StrictRandomSequential(name, List.of(children), seed);
    }

    @Override
    protected Result doExecute(Context context, Instant start) throws Throwable {
        List<Action> shuffled = new ArrayList<>(children());
        Random rng = seed().isPresent() ? new Random(seed().getAsLong()) : new Random();
        Collections.shuffle(shuffled, rng);

        List<Result> childResults = new ArrayList<>();
        boolean failed = false;
        for (Action child : shuffled) {
            if (failed) {
                childResults.add(child.skip(context.createChild(child)));
            } else {
                Result childResult = context.execute(child);
                childResults.add(childResult);
                if (childResult.status() == Result.Status.FAIL) {
                    failed = true;
                }
            }
        }
        return Result.of(
                this, computeStatus(childResults), durationSince(start), findFailure(childResults), childResults);
    }
}
