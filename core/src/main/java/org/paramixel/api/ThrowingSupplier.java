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
 * Supplies a value and may throw a checked exception.
 *
 * <p>Paramixel uses this type for instance factories and resource factories so that test code can acquire
 * fixtures and secondary resources without wrapping every call in a {@code try}/{@code catch} block.
 *
 * @param <T> the supplied value type
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {

    /**
     * Produces a value.
     *
     * @return the value; an instance factory must not return {@code null}
     * @throws Throwable if production fails
     */
    T get() throws Throwable;
}
