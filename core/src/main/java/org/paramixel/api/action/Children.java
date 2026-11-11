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

package org.paramixel.api.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * The mutable child list and shuffle settings shared by {@link Parallel.Builder} and
 * {@link Sequential.Builder}.
 *
 * <p>Both builders offer the same child operations, so the behavior lives here once and each builder exposes thin
 * public methods that delegate. This keeps the two containers identical without adding a public base type.
 *
 * <p>Not thread-safe; builders are not thread-safe.
 */
final class Children {

    private final List<Action> actions = new ArrayList<>();
    private boolean shuffled;
    private long seed;

    /**
     * Creates an empty child list.
     */
    Children() {}

    /**
     * Adds one child, building a builder argument immediately.
     *
     * @param action the child action or builder; must not be {@code null}
     * @throws NullPointerException if {@code action} is {@code null} or builds to {@code null}
     */
    void add(final ActionBuilder action) {
        Objects.requireNonNull(action, "action is null");
        actions.add(Objects.requireNonNull(action.build(), "action.build() returned null"));
    }

    /**
     * Adds one child per supplied builder, in iteration order.
     *
     * @param builders the child actions or builders; must not be {@code null}
     * @throws NullPointerException if {@code builders} or an element is {@code null}, or an element builds to
     *     {@code null}
     */
    void addAll(final Iterable<? extends ActionBuilder> builders) {
        Objects.requireNonNull(builders, "actions is null");
        for (var builder : builders) {
            add(builder);
        }
    }

    /**
     * Adds one child per item by calling the mapper once per item, in iteration order, at define time.
     *
     * <p>The mapper runs eagerly: it is not deferred to execution and must not touch a fixture. A throw from the
     * mapper is a define-time failure, and children added before the throw stay on the builder.
     *
     * @param <T> the item type
     * @param items the items to map; must not be {@code null}
     * @param mapper the mapper from item to child; must not be {@code null} and must not return {@code null}
     * @throws NullPointerException if {@code items}, {@code mapper}, or a mapped result is {@code null}
     */
    <T> void each(final Iterable<T> items, final Function<? super T, ? extends ActionBuilder> mapper) {
        Objects.requireNonNull(items, "items is null");
        Objects.requireNonNull(mapper, "mapper is null");
        for (var item : items) {
            add(Objects.requireNonNull(mapper.apply(item), "mapper returned null"));
        }
    }

    /**
     * Configures a random shuffle with a seed generated now.
     *
     * @return the seed that will be used at build time
     */
    long shuffle() {
        return shuffle(ThreadLocalRandom.current().nextLong());
    }

    /**
     * Configures a shuffle with the supplied seed.
     *
     * @param seed the PRNG seed
     * @return the seed
     */
    long shuffle(final long seed) {
        this.shuffled = true;
        this.seed = seed;
        return seed;
    }

    /**
     * Returns whether children are shuffled at build time.
     *
     * @return {@code true} when a shuffle is configured
     */
    boolean isShuffled() {
        return shuffled;
    }

    /**
     * Returns the configured shuffle seed.
     *
     * @return the seed, meaningful only when {@link #isShuffled()} is {@code true}
     */
    long seed() {
        return seed;
    }

    /**
     * Returns an immutable, validated snapshot of the children, shuffled when configured.
     *
     * @return the immutable child list; never {@code null}
     * @throws NullPointerException if a child is {@code null}
     */
    List<Action> snapshot() {
        var copy = new ArrayList<>(actions);
        if (shuffled) {
            Collections.shuffle(copy, new Random(seed));
        }
        for (var action : copy) {
            Objects.requireNonNull(action, "children contains null element");
        }
        return List.copyOf(copy);
    }
}
