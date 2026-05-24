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
 * A consumer that accepts an instance and may throw a checked exception.
 *
 * @param <T> the type of instance consumed
 */
@FunctionalInterface
public interface ThrowingConsumer<T> {

    /**
     * Performs the operation on the supplied instance.
     *
     * @param instance the instance; never {@code null}
     * @throws Throwable if the operation fails
     */
    void accept(T instance) throws Throwable;
}
