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

import java.util.Objects;
import java.util.Optional;

/**
 * Provides runtime services for an active action execution.
 *
 * <p>Actions use this context to access configuration and fixture instances. Descriptor traversal,
 * scheduling, status transitions, and listener notifications are owned by the runner and scheduler.
 */
public interface Context {

    /**
     * Returns the configuration for the current run.
     *
     * @return the run configuration; never {@code null}
     */
    Configuration configuration();

    /**
     * Returns the current fixture instance for the requested type.
     *
     * @param <T> the requested instance type
     * @param type the requested instance class; must not be {@code null}
     * @return the instance, or empty when no compatible instance is available
     */
    <T> Optional<T> instance(Class<T> type);

    /**
     * Returns the current fixture instance for the requested type, throwing if none is available.
     *
     * @param <T> the requested instance type
     * @param type the requested instance class; must not be {@code null}
     * @return the instance
     * @throws NullPointerException if {@code type} is {@code null}
     * @throws IllegalStateException if no compatible instance is available
     */
    default <T> T requireInstance(final Class<T> type) {
        Objects.requireNonNull(type, "type is null");

        return instance(type)
                .orElseThrow(() -> new IllegalStateException("No instance is available for " + type.getName()));
    }

    /**
     * Returns a context consumer that retrieves an instance of the specified type from the context
     * and passes it to the provided consumer.
     *
     * @param <T> the type of the instance to retrieve and consume
     * @param type the class of the instance to retrieve; must not be {@code null}
     * @param consumer the consumer to accept the instance; must not be {@code null}
     * @return the context consumer that performs the instance retrieval and consumption
     * @throws NullPointerException if {@code type} or {@code consumer} is {@code null}
     */
    static <T> ContextConsumer withInstance(final Class<T> type, final InstanceConsumer<? super T> consumer) {
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(consumer, "consumer is null");

        return context -> consumer.accept(context.requireInstance(type));
    }
}
