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
import nonapi.org.paramixel.action.FixtureConsumer;
import nonapi.org.paramixel.action.StepAccessors;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Context;
import org.paramixel.api.ContextConsumer;
import org.paramixel.api.InstanceConsumer;

/**
 * Terminal action that invokes user code during execution.
 *
 * <p>There are two forms. {@link #step(String, ContextConsumer)} receives the execution {@link
 * Context}. {@link #step(String, Class, InstanceConsumer)} receives the fixture of the requested type
 * from the nearest enclosing {@code Instance}, which is how a test method is bound to its fixture.
 *
 * <p>A step fails by throwing {@link org.paramixel.api.exception.FailException}, skips by throwing
 * {@link org.paramixel.api.exception.SkipException}, and aborts by throwing
 * {@link org.paramixel.api.exception.AbortException}. Any other throwable fails the step.
 */
public final class Step implements Action {

    static {
        StepAccessors.register(step -> step.consumer);
    }

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
    public static Step step(final String displayName, final ContextConsumer consumer) {
        return new Step(displayName, consumer);
    }

    /**
     * Creates a step that receives the fixture of the supplied type.
     *
     * <p>The fixture comes from the nearest enclosing {@code Instance} that provides {@code type}, and
     * is created lazily when that {@code Instance} node executes. The whole run is rejected before any
     * user code runs when no enclosing {@code Instance} provides a compatible type.
     *
     * @param <T> the fixture type
     * @param displayName the action display name; must not be {@code null} or blank
     * @param type the fixture type the step requires; must not be {@code null}
     * @param consumer the consumer that receives the fixture; must not be {@code null}
     * @return the step action
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public static <T> Step step(
            final String displayName, final Class<T> type, final InstanceConsumer<? super T> consumer) {
        return step(displayName, new FixtureConsumer<>(type, consumer));
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
