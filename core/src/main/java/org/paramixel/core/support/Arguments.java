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
 * Shared argument-validation helpers for Paramixel internals and support APIs.
 */
public class Arguments {

    private Arguments() {}

    /**
     * Returns the supplied string when it is non-blank.
     *
     * @param value the string to validate
     * @param message the exception message to use when validation fails
     * @return {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String requireNonBlank(String value, String message) {
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
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String requireNonBlank(String value, Supplier<String> messageSupplier) {
        Objects.requireNonNull(value);
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
    public static String requireNotEmpty(String value, String message) {
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
    public static int requirePositive(int value, String message) {
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
    public static long requireNonNegative(long value, String message) {
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
    public static void require(boolean condition, String message) {
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
    public static void require(boolean condition, Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, "messageSupplier must not be null");
        if (!condition) {
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
     * @throws IllegalArgumentException if {@code collection} is empty
     */
    public static <T> Collection<T> requireNonEmpty(Collection<T> collection, String message) {
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
     * @throws NullPointerException if any element is {@code null}
     */
    public static <T> Collection<T> requireNoNullElements(Collection<T> collection, String message) {
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
     * @throws NullPointerException if any element is {@code null}
     */
    public static <T> T[] requireNoNullElements(T[] array, String message) {
        for (T element : array) {
            Objects.requireNonNull(element, message);
        }
        return array;
    }
}
