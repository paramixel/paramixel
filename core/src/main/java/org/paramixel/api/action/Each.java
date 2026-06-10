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

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Factory methods for generating action subtrees from iterable inputs.
 *
 * <p>Each method creates a grouping action and adds one child per input item by applying
 * the supplied mapper immediately. Empty inputs produce a grouping action with no children.
 */
public final class Each {

    private Each() {
        // Intentionally empty
    }

    /**
     * Maps an input item to a child action representation accepted by {@link Each}.
     *
     * @param <T> the input item type
     * @param <R> the mapped result type; supported results are {@link Action} and {@link Builder}
     */
    @FunctionalInterface
    public interface ActionMapper<T, R> extends Function<T, R> {

        /**
         * Maps the input item to a child action representation.
         *
         * @param item the input item
         * @return the child action representation; must not be {@code null}
         */
        @Override
        R apply(T item);
    }

    /**
     * Maps an input item to a Paramixel-owned child action {@link Builder}.
     *
     * @param <T> the input item type
     */
    @FunctionalInterface
    public interface BuilderMapper<T> extends ActionMapper<T, Builder> {

        /**
         * Maps the input item to a child action builder.
         *
         * @param item the input item
         * @return the child action builder; must not be {@code null}
         */
        @Override
        Builder apply(T item);
    }

    /**
     * Creates a sequential grouping action with one child per iterable item.
     *
     * <p>The returned builder uses the same defaults as {@link Sequential#builder(String)}. Callers
     * may further configure it, for example with {@link Sequential.Builder#independent()}. Mapper
     * results may be immutable {@link Action} instances or Paramixel-owned {@link Builder} instances.
     * Builder results are built immediately.
     *
     * @param <T> the type of items in the iterable
     * @param displayName the grouping action display name; must not be {@code null} or blank
     * @param items the items to iterate over; must not be {@code null}
     * @param mapper the function that maps each item to a child action or builder; must not be {@code null}
     * @return a sequential builder containing one mapped child per item
     * @throws NullPointerException if {@code displayName}, {@code items}, {@code mapper}, a mapped result,
     *     or a built action is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank or a mapped result is neither an
     *     {@link Action} nor a {@link Builder}
     */
    public static <T> Sequential.Builder sequential(
            final String displayName, final Iterable<T> items, final ActionMapper<? super T, ?> mapper) {
        Objects.requireNonNull(items, "items is null");
        Objects.requireNonNull(mapper, "mapper is null");
        var builder = Sequential.builder(displayName);
        for (T item : items) {
            builder.child(toAction(mapper.apply(item)));
        }
        return builder;
    }

    /**
     * Creates a sequential grouping action with one child per stream item.
     *
     * <p>The stream is materialized immediately and then delegated to
     * {@link #sequential(String, Iterable, ActionMapper)}. Mapper results may be immutable
     * {@link Action} instances or Paramixel-owned {@link Builder} instances. Builder results are built
     * immediately.
     *
     * @param <T> the type of items in the stream
     * @param displayName the grouping action display name; must not be {@code null} or blank
     * @param items the items to iterate over; must not be {@code null}
     * @param mapper the function that maps each item to a child action or builder; must not be {@code null}
     * @return a sequential builder containing one mapped child per item
     * @throws NullPointerException if {@code displayName}, {@code items}, {@code mapper}, a mapped result,
     *     or a built action is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank or a mapped result is neither an
     *     {@link Action} nor a {@link Builder}
     */
    public static <T> Sequential.Builder sequential(
            final String displayName, final Stream<T> items, final ActionMapper<? super T, ?> mapper) {
        Objects.requireNonNull(items, "items is null");
        return sequential(displayName, items.toList(), mapper);
    }

    /**
     * Creates a parallel grouping action with one child per iterable item.
     *
     * <p>The returned builder uses the same defaults as {@link Parallel#builder(String)}. Callers
     * may further configure it, for example with {@link Parallel.Builder#parallelism(int)}. Mapper
     * results may be immutable {@link Action} instances or Paramixel-owned {@link Builder} instances.
     * Builder results are built immediately.
     *
     * @param <T> the type of items in the iterable
     * @param displayName the grouping action display name; must not be {@code null} or blank
     * @param items the items to iterate over; must not be {@code null}
     * @param mapper the function that maps each item to a child action or builder; must not be {@code null}
     * @return a parallel builder containing one mapped child per item
     * @throws NullPointerException if {@code displayName}, {@code items}, {@code mapper}, a mapped result,
     *     or a built action is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank or a mapped result is neither an
     *     {@link Action} nor a {@link Builder}
     */
    public static <T> Parallel.Builder parallel(
            final String displayName, final Iterable<T> items, final ActionMapper<? super T, ?> mapper) {
        Objects.requireNonNull(items, "items is null");
        Objects.requireNonNull(mapper, "mapper is null");
        var builder = Parallel.builder(displayName);
        for (T item : items) {
            builder.child(toAction(mapper.apply(item)));
        }
        return builder;
    }

    /**
     * Creates a parallel grouping action with one child per stream item.
     *
     * <p>The stream is materialized immediately and then delegated to
     * {@link #parallel(String, Iterable, ActionMapper)}. Mapper results may be immutable {@link Action}
     * instances or Paramixel-owned {@link Builder} instances. Builder results are built immediately.
     *
     * @param <T> the type of items in the stream
     * @param displayName the grouping action display name; must not be {@code null} or blank
     * @param items the items to iterate over; must not be {@code null}
     * @param mapper the function that maps each item to a child action or builder; must not be {@code null}
     * @return a parallel builder containing one mapped child per item
     * @throws NullPointerException if {@code displayName}, {@code items}, {@code mapper}, a mapped result,
     *     or a built action is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank or a mapped result is neither an
     *     {@link Action} nor a {@link Builder}
     */
    public static <T> Parallel.Builder parallel(
            final String displayName, final Stream<T> items, final ActionMapper<? super T, ?> mapper) {
        Objects.requireNonNull(items, "items is null");
        return parallel(displayName, items.toList(), mapper);
    }

    private static Action toAction(final Object mapped) {
        Objects.requireNonNull(mapped, "action is null");
        if (mapped instanceof Action action) {
            return action;
        }
        if (mapped instanceof Builder builder) {
            return Objects.requireNonNull(builder.build(), "builder.build() returned null");
        }
        throw new IllegalArgumentException(
                "mapper returned unsupported action type: " + mapped.getClass().getName());
    }
}
