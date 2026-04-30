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

package org.paramixel.core.support;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility methods for validating method arguments.
 *
 * <p>Provides static checks that throw {@link IllegalArgumentException} (or
 * {@link NullPointerException}) when arguments violate constraints. Intended as
 * a fluent alternative to manual {@code if}/{@code throw} guards.
 *
 * @see java.util.Objects
 */
public class Arguments {

    /** Suppresses default constructor for utility class. */
    private Arguments() {}

    /**
     * Requires a string that is not {@code null} and not blank.
     *
     * @param value The string to check; must not be null.
     * @param message The exception message if the string is blank.
     * @return The validated string; never null.
     * @throws NullPointerException If {@code value} is null.
     * @throws IllegalArgumentException If {@code value} is blank.
     */
    public static String requireNonBlank(String value, String message) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Requires a string that is not {@code null} and not blank.
     *
     * @param value The string to check; must not be null.
     * @param messageSupplier The supplier for the exception message if the string is blank; must not be null.
     * @return The validated string; never null.
     * @throws NullPointerException If {@code value} is null or {@code messageSupplier} is null.
     * @throws IllegalArgumentException If {@code value} is blank.
     */
    public static String requireNonBlank(String value, Supplier<String> messageSupplier) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return value;
    }

    /**
     * Requires a string that is not {@code null} and not empty.
     *
     * @param value The string to check; must not be null.
     * @param message The exception message if the string is empty.
     * @return The validated string; never null.
     * @throws NullPointerException If {@code value} is null.
     * @throws IllegalArgumentException If {@code value} is empty.
     */
    public static String requireNotEmpty(String value, String message) {
        Objects.requireNonNull(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Requires a positive integer value.
     *
     * @param value The value to check.
     * @param message The exception message if the value is not positive.
     * @return The validated value.
     * @throws IllegalArgumentException If {@code value} is zero or negative.
     */
    public static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Requires a non-negative long value.
     *
     * @param value The value to check.
     * @param message The exception message if the value is negative.
     * @return The validated value.
     * @throws IllegalArgumentException If {@code value} is negative.
     */
    public static long requireNonNegative(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Requires a condition to be true.
     *
     * @param condition The condition to check.
     * @param message The exception message if the condition is false.
     * @throws IllegalArgumentException If {@code condition} is false.
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Requires a condition to be true.
     *
     * @param condition The condition to check.
     * @param messageSupplier The supplier for the exception message if the condition is false; must not be null.
     * @throws NullPointerException If {@code messageSupplier} is null.
     * @throws IllegalArgumentException If {@code condition} is false.
     */
    public static void require(boolean condition, Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, "messageSupplier must not be null");
        if (!condition) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
    }

    /**
     * Requires a collection that is not empty.
     *
     * @param <T> the element type of the collection
     * @param collection the collection to check; must not be null
     * @param message the exception message if the collection is empty
     * @return the validated collection; never null
     * @throws NullPointerException if {@code collection} is null
     * @throws IllegalArgumentException if {@code collection} is empty
     */
    public static <T> Collection<T> requireNonEmpty(Collection<T> collection, String message) {
        if (collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * Requires a collection where no element is {@code null}.
     *
     * @param <T> the element type of the collection
     * @param collection the collection to check; must not be null
     * @param message the exception message if any element is null
     * @return the validated collection; never null
     * @throws NullPointerException if {@code collection} is null or contains null elements
     */
    public static <T> Collection<T> requireNoNullElements(Collection<T> collection, String message) {
        for (T element : collection) {
            Objects.requireNonNull(element, message);
        }
        return collection;
    }

    /**
     * Requires an array where no element is {@code null}.
     *
     * @param <T> the element type of the array
     * @param array the array to check; must not be null
     * @param message the exception message if any element is null
     * @return the validated array; never null
     * @throws NullPointerException if {@code array} is null or contains null elements
     */
    public static <T> T[] requireNoNullElements(T[] array, String message) {
        for (T element : array) {
            Objects.requireNonNull(element, message);
        }
        return array;
    }
}
