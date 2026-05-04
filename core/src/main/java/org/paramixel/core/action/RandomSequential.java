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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Random;
import org.paramixel.core.Action;
import org.paramixel.core.Context;
import org.paramixel.core.Result;
import org.paramixel.core.spi.DefaultResult;
import org.paramixel.core.support.Arguments;

/**
 * Executes child actions sequentially in randomized order.
 *
 * <p>All children are executed even when earlier children fail. An optional seed allows deterministic ordering.
 */
public class RandomSequential extends BranchAction {

    private final OptionalLong seed;

    /**
     * Creates a randomized sequential action with an optional deterministic seed.
     *
     * @param name the action name
     * @param children the child actions
     * @param seed the optional shuffle seed
     */
    protected RandomSequential(String name, List<Action> children, OptionalLong seed) {
        super(children);
        this.name = validateName(name);
        this.seed = seed;
    }

    /**
     * Creates a randomized sequential action.
     *
     * @param name the action name
     * @param children the child actions
     * @return a new randomized sequential action
     */
    public static RandomSequential of(String name, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        RandomSequential instance = new RandomSequential(name, children, OptionalLong.empty());
        instance.initialize();
        return instance;
    }

    /**
     * Creates a randomized sequential action.
     *
     * @param name the action name
     * @param children the child actions
     * @return a new randomized sequential action
     */
    public static RandomSequential of(String name, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        RandomSequential instance = new RandomSequential(name, List.of(children), OptionalLong.empty());
        instance.initialize();
        return instance;
    }

    /**
     * Creates a randomized sequential action with deterministic shuffling.
     *
     * @param name the action name
     * @param seed the shuffle seed
     * @param children the child actions
     * @return a new randomized sequential action
     */
    public static RandomSequential of(String name, long seed, List<Action> children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Arguments.requireNonEmpty(children, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        RandomSequential instance = new RandomSequential(name, children, OptionalLong.of(seed));
        instance.initialize();
        return instance;
    }

    /**
     * Creates a randomized sequential action with deterministic shuffling.
     *
     * @param name the action name
     * @param seed the shuffle seed
     * @param children the child actions
     * @return a new randomized sequential action
     */
    public static RandomSequential of(String name, long seed, Action... children) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(children, "children must not be null");
        Arguments.require(children.length > 0, "children must not be empty");
        Arguments.requireNoNullElements(children, "children must not contain null elements");
        RandomSequential instance = new RandomSequential(name, List.of(children), OptionalLong.of(seed));
        instance.initialize();
        return instance;
    }

    /**
     * Returns the configured shuffle seed.
     *
     * @return the seed, or an empty {@link OptionalLong} when iteration order is non-deterministic
     */
    public OptionalLong seed() {
        return seed;
    }

    /**
     * Executes all children sequentially in shuffled order.
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

        List<Action> shuffled = new ArrayList<>(children);
        Random random = seed.isPresent() ? new Random(seed.getAsLong()) : new Random();
        Collections.shuffle(shuffled, random);

        for (Action child : shuffled) {
            Result childResult = child.execute(context.createChild());
            result.addChild(childResult);
        }

        result.setStatus(computeStatus(result.getChildren()));
        result.setElapsedTime(Duration.between(start, Instant.now()));
        context.getListener().afterAction(result);
        return result;
    }
}
