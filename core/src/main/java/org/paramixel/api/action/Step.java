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
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;
import org.paramixel.api.internal.support.Arguments;
import org.paramixel.spi.action.ExecutionContext;
import org.paramixel.spi.action.Mode;

/**
 * Leaf action that invokes a user-supplied consumer during run-mode execution.
 *
 * @param <T> the type accepted by the consumer
 */
public final class Step<T> implements Action<T> {

    private static final String KIND = "Step";

    private final String name;
    private final String kind;
    private final ThrowingConsumer<T> consumer;

    private Step(final String name, final String kind, final ThrowingConsumer<T> consumer) {
        this.name = Arguments.requireValidName(name);
        this.kind = Arguments.requireNonBlank(
                Objects.requireNonNull(kind, "kind must not be null"), "kind must not be blank");
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
    }

    /**
     * Creates a step with the supplied name and consumer.
     *
     * @param <T> the consumer input type
     * @param name the action name; must not be {@code null} or blank
     * @param consumer the consumer to invoke; must not be {@code null}
     * @return the step action
     * @throws NullPointerException if {@code name} or {@code consumer} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public static <T> Step<T> of(final String name, final ThrowingConsumer<T> consumer) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(consumer, "consumer must not be null");
        return new Step<>(name, KIND, consumer);
    }

    /**
     * Creates a step with the supplied name, kind, and consumer.
     *
     * @param <T> the consumer input type
     * @param name the action name; must not be {@code null} or blank
     * @param kind the action kind; must not be {@code null} or blank
     * @param consumer the consumer to invoke; must not be {@code null}
     * @return the step action
     * @throws NullPointerException if {@code name}, {@code kind}, or {@code consumer} is {@code null}
     * @throws IllegalArgumentException if {@code name} or {@code kind} is blank
     */
    public static <T> Step<T> of(final String name, final String kind, final ThrowingConsumer<T> consumer) {
        Objects.requireNonNull(name, "name must not be null");
        Arguments.requireNonBlank(name, "name must not be blank");
        Objects.requireNonNull(kind, "kind must not be null");
        Arguments.requireNonBlank(kind, "kind must not be blank");
        Objects.requireNonNull(consumer, "consumer must not be null");
        return new Step<>(name, kind, consumer);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String kind() {
        return kind;
    }

    /**
     * Returns a new step with the same name and consumer but a different kind.
     *
     * <p>The original step is not modified. The returned step is independent
     * and may be reused without affecting this step.
     *
     * @param kind the kind for the new step; must not be {@code null} or blank
     * @return a new step with the supplied kind
     * @throws NullPointerException if {@code kind} is {@code null}
     * @throws IllegalArgumentException if {@code kind} is blank
     */
    public Step<T> kind(final String kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        Arguments.requireNonBlank(kind, "kind must not be blank");
        return new Step<>(this.name, kind, this.consumer);
    }

    @Override
    public void execute(final ExecutionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        var descriptor = context.descriptor();
        var listener = context.listener();
        listener.onBeforeExecution(descriptor);
        context.setStatus(Status.RUNNING);
        try {
            var mode = descriptor.metadata().mode();
            if (mode != Mode.RUN) {
                context.setStatus(mode.toStatus());
            } else {
                run(context);
                context.setStatus(Status.PASSED);
            }
        } catch (Throwable t) {
            context.setStatus(Status.fromThrowable(t));
        }
        listener.onAfterExecution(descriptor);
    }

    private void run(final ExecutionContext context) throws Throwable {
        var instanceOpt = context.instance(Object.class);
        if (instanceOpt.isPresent()) {
            @SuppressWarnings("unchecked")
            T value = (T) instanceOpt.get();
            consumer.accept(value);
        } else {
            @SuppressWarnings("unchecked")
            T ctx = (T) context;
            consumer.accept(ctx);
        }
    }
}
