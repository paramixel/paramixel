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

package org.paramixel.core.internal.util;

import java.util.Objects;
import java.util.function.Supplier;

public class Arguments {

    private Arguments() {}

    public static String requireNotBlank(String value, String message) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static String requireNotBlank(String value, Supplier<String> messageSupplier) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
        return value;
    }

    public static String requireNotEmpty(String value, String message) {
        Objects.requireNonNull(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static long requireNonNegative(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void require(boolean condition, Supplier<String> messageSupplier) {
        Objects.requireNonNull(messageSupplier, "messageSupplier must not be null");
        if (!condition) {
            throw new IllegalArgumentException(messageSupplier.get());
        }
    }
}
