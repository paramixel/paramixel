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
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Context;
import org.paramixel.api.ContextConsumer;

/**
 * Terminal action that invokes a user-supplied consumer during run-mode execution.
 *
 * <p>The consumer receives the execution {@link Context}. When wrapped in an
 * {@link Instance} action, the context provides access to the fixture instance
 * via {@link Context#instance(Class)}.
 */
public final class Step implements Action {

    private final String displayName;
    private final ContextConsumer consumer;

    private Step(final String displayName, final ContextConsumer consumer) {
        Objects.requireNonNull(displayName, "displayName is null");
        this.displayName = Arguments.requireNonBlank(displayName, "displayName is blank");
        this.consumer = Objects.requireNonNull(consumer, "consumer is null");
    }

    /**
     * Creates a step with the supplied name and consumer.
     *
     * @param displayName the action display name; must not be {@code null} or blank
     * @param consumer the consumer to invoke; must not be {@code null}
     * @return the step action
     * @throws NullPointerException if {@code displayName} or {@code consumer} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static Step of(final String displayName, final ContextConsumer consumer) {
        Objects.requireNonNull(displayName, "displayName is null");
        Arguments.requireNonBlank(displayName, "displayName is blank");
        Objects.requireNonNull(consumer, "consumer is null");
        return new Step(displayName, consumer);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the step executable.
     *
     * @return the step executable; never {@code null}
     */
    public ContextConsumer throwableConsumer() {
        return consumer;
    }
}
