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

package nonapi.org.paramixel.support;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Validates method and constructor arguments by rejecting blank strings, empty collections,
 * non-positive integers, and failed conditions with descriptive exceptions.
 *
 * <p>Each validation method either returns the validated value or throws
 * {@link IllegalArgumentException} or {@link NullPointerException} with a caller-supplied
 * message. This class cannot be instantiated.
 */
public class Arguments {

    private Arguments() {
        // Intentionally empty
    }

    /**
     * Returns the supplied string when it is non-blank.
     *
     * @param value the string to validate
     * @param message the exception message to use when validation fails
     * @return {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String requireNonBlank(final String value, final String message) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Returns the supplied string when it is non-blank.
     *
     * @param value the string to validate
     * @param messageSupplier the exception message supplier to use when validation fails
     * @return {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String requireNonBlank(final String value, final Supplier<String> messageSupplier) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(messageSupplier);
        if (value.isBlank()) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return value;
    }

    /**
     * Returns the supplied string when it is non-empty.
     *
     * @param value the string to validate
     * @param message the exception message to use when validation fails
     * @return {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is empty
     */
    public static String requireNotEmpty(final String value, final String message) {
        Objects.requireNonNull(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Returns the supplied integer when it is positive.
     *
     * @param value the value to validate
     * @param message the exception message to use when validation fails
     * @return {@code value}
     * @throws IllegalArgumentException if {@code value} is not positive
     */
    public static int requirePositive(final int value, final String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Returns the supplied long when it is non-negative.
     *
     * @param value the value to validate
     * @param message the exception message to use when validation fails
     * @return {@code value}
     * @throws IllegalArgumentException if {@code value} is negative
     */
    public static long requireNonNegative(final long value, final String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Validates that the supplied condition is {@code true}.
     *
     * @param condition the condition to validate
     * @param message the exception message to use when validation fails
     * @throws IllegalArgumentException if {@code condition} is {@code false}
     */
    public static void requireTrue(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Validates that the supplied condition is {@code true}.
     *
     * @param condition the condition to validate
     * @param messageSupplier the exception message supplier to use when validation fails
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     * @throws IllegalArgumentException if {@code condition} is {@code false}
     */
    public static void requireTrue(final boolean condition, final Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, "messageSupplier is null");
        if (!condition) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
    }

    /**
     * Validates that the supplied condition is {@code false}.
     *
     * @param condition the condition to validate
     * @param message the exception message to use when validation fails
     * @throws IllegalArgumentException if {@code condition} is {@code true}
     */
    public static void requireFalse(final boolean condition, final String message) {
        if (condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Validates that the supplied condition is {@code false}.
     *
     * @param condition the condition to validate
     * @param messageSupplier the exception message supplier to use when validation fails
     * @throws NullPointerException if {@code messageSupplier} is {@code null}
     * @throws IllegalArgumentException if {@code condition} is {@code true}
     */
    public static void requireFalse(final boolean condition, final Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, "messageSupplier is null");
        if (condition) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
    }

    /**
     * Returns the supplied collection when it is non-empty.
     *
     * @param <T> the collection element type
     * @param collection the collection to validate
     * @param message the exception message to use when validation fails
     * @return {@code collection}
     * @throws NullPointerException if {@code collection} is {@code null}
     * @throws IllegalArgumentException if {@code collection} is empty
     */
    public static <T> Collection<T> requireNonEmpty(final Collection<T> collection, final String message) {
        Objects.requireNonNull(collection);
        if (collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * Returns the supplied collection when none of its elements are {@code null}.
     *
     * @param <T> the collection element type
     * @param collection the collection to validate
     * @param message the exception message to use when validation fails
     * @return {@code collection}
     * @throws NullPointerException if {@code collection} is {@code null}
     * @throws NullPointerException if any element is {@code null}
     */
    public static <T> Collection<T> requireNoNullElements(final Collection<T> collection, final String message) {
        Objects.requireNonNull(collection);
        for (T element : collection) {
            Objects.requireNonNull(element, message);
        }
        return collection;
    }

    /**
     * Returns the supplied array when none of its elements are {@code null}.
     *
     * @param <T> the array element type
     * @param array the array to validate
     * @param message the exception message to use when validation fails
     * @return {@code array}
     * @throws NullPointerException if {@code array} is {@code null}
     * @throws NullPointerException if any element is {@code null}
     */
    public static <T> T[] requireNoNullElements(final T[] array, final String message) {
        Objects.requireNonNull(array, "array is null");
        for (T element : array) {
            Objects.requireNonNull(element, message);
        }
        return array;
    }

    /**
     * Returns the supplied object when it is an instance of the specified type.
     *
     * @param <T> the expected type
     * @param object the object to validate
     * @param type the expected class type
     * @param message the exception message to use when validation fails
     * @return {@code object} cast to {@code T}
     * @throws NullPointerException if {@code object} or {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code object} is not an instance of {@code type}
     */
    public static <T> T requireInstanceOf(final Object object, final Class<T> type, final String message) {
        Objects.requireNonNull(object, "object is null");
        Objects.requireNonNull(type, "type is null");
        if (!type.isInstance(object)) {
            throw new IllegalArgumentException(message);
        }
        return type.cast(object);
    }

    /**
     * Returns the supplied object when it is an instance of the specified type.
     *
     * @param <T> the expected type
     * @param object the object to validate
     * @param type the expected class type
     * @param messageSupplier the exception message supplier to use when validation fails
     * @return {@code object} cast to {@code T}
     * @throws NullPointerException if {@code object}, {@code type}, or
     *     {@code messageSupplier} is {@code null}
     * @throws IllegalArgumentException if {@code object} is not an instance of {@code type}
     */
    public static <T> T requireInstanceOf(
            final Object object, final Class<T> type, final Supplier<String> messageSupplier) {
        Objects.requireNonNull(object, "object is null");
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(messageSupplier, "messageSupplier is null");
        if (!type.isInstance(object)) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return type.cast(object);
    }
}
