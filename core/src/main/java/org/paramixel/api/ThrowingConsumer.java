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

package org.paramixel.api;

/**
 * Consumes a value and may throw a checked exception.
 *
 * <p>Task bodies, lifecycle callbacks, and resource release callbacks use this type so that method references to
 * test methods with checked exceptions need no adapter.
 *
 * @param <T> the consumed value type
 */
@FunctionalInterface
public interface ThrowingConsumer<T> {

    /**
     * Consumes the supplied value.
     *
     * @param value the value; never {@code null} for a primary instance or an acquired resource
     * @throws Throwable if consumption fails
     */
    void accept(T value) throws Throwable;
}
