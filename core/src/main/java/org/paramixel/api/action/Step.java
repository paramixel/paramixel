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
import nonapi.org.paramixel.FrameworkException;
import nonapi.org.paramixel.support.Arguments;
import org.paramixel.api.Status;
import org.paramixel.api.ThrowingConsumer;

/**
 * Leaf action that invokes a user-supplied consumer during run-mode execution.
 *
 * <p>When this step is wrapped in an {@link Instance} action, the consumer receives
 * the fixture instance of type {@code T}. When executed without a containing instance,
 * the consumer receives the execution {@link Context}.
 *
 * @param <T> the type accepted by the consumer
 */
public final class Step<T> implements Action<T> {

    private static final String KIND = "Step";

    private final String name;
    private final String kind;
    private final ThrowingConsumer<T> consumer;

    private Step(final String name, final String kind, final ThrowingConsumer<T> consumer) {
        Objects.requireNonNull(name, "name is null");
        this.name = Arguments.requireNonBlank(name, "name is blank");
        Objects.requireNonNull(kind, "kind is null");
        this.kind = Arguments.requireNonBlank(kind, "kind is blank");
        this.consumer = Objects.requireNonNull(consumer, "consumer is null");
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
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
        Objects.requireNonNull(consumer, "consumer is null");
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
        Objects.requireNonNull(name, "name is null");
        Arguments.requireNonBlank(name, "name is blank");
        Objects.requireNonNull(kind, "kind is null");
        Arguments.requireNonBlank(kind, "kind is blank");
        Objects.requireNonNull(consumer, "consumer is null");
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
        Objects.requireNonNull(kind, "kind is null");
        Arguments.requireNonBlank(kind, "kind is blank");
        return new Step<>(this.name, kind, this.consumer);
    }

    @Override
    public void execute(final Context context) {
        Objects.requireNonNull(context, "context is null");
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
            context.setStatus(Status.fromThrowable(FrameworkException.wrap(t)));
        }
        listener.onAfterExecution(descriptor);
    }

    private void run(final Context context) throws Throwable {
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
