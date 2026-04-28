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
import java.util.OptionalLong;
import java.util.Random;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;

/**
 * A {@link Sequential} action that executes child actions in random order.
 *
 * <p>All children execute regardless of failures. Use {@code RandomSequential}
 * when you want to verify that tests do not depend on execution order.
 */
public class RandomSequential extends Sequential {

    private final OptionalLong seed;

    protected RandomSequential(String name, List<Action> children, Long seed) {
        super(name, children);
        this.seed = seed != null ? OptionalLong.of(seed) : OptionalLong.empty();
    }

    /**
     * Creates a random sequential action with no seed (non-deterministic order).
     *
     * @param name The action name; must not be null.
     * @param children The child actions; must not be null or empty.
     * @return A new RandomSequential action.
     */
    public static RandomSequential of(String name, List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        return new RandomSequential(name, children, null);
    }

    /**
     * Creates a random sequential action with no seed (non-deterministic order).
     *
     * @param name The action name; must not be null.
     * @param children The child actions; must not be null or empty.
     * @return A new RandomSequential action.
     */
    public static RandomSequential of(String name, Action... children) {
        if (children.length == 0) {
            throw new IllegalArgumentException("children must not be empty");
        }
        return new RandomSequential(name, List.of(children), null);
    }

    /**
     * Creates a random sequential action with a seed for reproducibility.
     *
     * @param name The action name; must not be null.
     * @param seed The random seed for reproducible ordering.
     * @param children The child actions; must not be null or empty.
     * @return A new RandomSequential action.
     */
    public static RandomSequential of(String name, long seed, List<Action> children) {
        Objects.requireNonNull(children, "children must not be null");
        return new RandomSequential(name, children, seed);
    }

    /**
     * Creates a random sequential action with a seed for reproducibility.
     *
     * @param name The action name; must not be null.
     * @param seed The random seed for reproducible ordering.
     * @param children The child actions; must not be null or empty.
     * @return A new RandomSequential action.
     */
    public static RandomSequential of(String name, long seed, Action... children) {
        if (children.length == 0) {
            throw new IllegalArgumentException("children must not be empty");
        }
        return new RandomSequential(name, List.of(children), seed);
    }

    /**
     * Returns the seed used for random ordering, if one was provided.
     *
     * @return An {@link OptionalLong} containing the seed, or empty if unseeded.
     */
    public OptionalLong seed() {
        return seed;
    }

    @Override
    protected Result doExecute(Context context, Instant start) throws Throwable {
        List<Action> shuffled = new ArrayList<>(children());
        Collections.shuffle(shuffled, seed.isPresent() ? new Random(seed.getAsLong()) : new Random());

        List<Result> childResults = new ArrayList<>();
        for (Action child : shuffled) {
            childResults.add(context.execute(child));
        }
        return Result.of(
                this, computeStatus(childResults), durationSince(start), findFailure(childResults), childResults);
    }
}
