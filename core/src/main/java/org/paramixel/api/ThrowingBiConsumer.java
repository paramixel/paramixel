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
 * Consumes two values and may throw a checked exception.
 *
 * <p>Used by the explicit context-aware callback methods such as
 * {@link TaskBuilder#stepWithContext(String, ThrowingBiConsumer)}. A dedicated method name, rather than an overload of
 * the one-argument callbacks, keeps ordinary method-reference inference unambiguous.
 *
 * @param <T> the first consumed value type
 * @param <U> the second consumed value type
 */
@FunctionalInterface
public interface ThrowingBiConsumer<T, U> {

    /**
     * Consumes the supplied values.
     *
     * @param first the primary instance
     * @param second the execution context
     * @throws Throwable if consumption fails
     */
    void accept(T first, U second) throws Throwable;
}
