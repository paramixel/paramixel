/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.EngineContext;

/**
 * Collects argument values and per-class parallelism during discovery.
 *
 * <p>This type implements {@link ArgumentsCollector} for the engine. Collector methods add argument
 * values via {@link #addArgument(Object)} / {@link #addArguments(Object...)} and may override
 * per-test-class parallelism via {@link #setParallelism(int)}.
 *
 * <p><b>Thread safety</b>
 * <p>This implementation is not thread-safe. The engine invokes an argument supplier on a single
 * thread during discovery.
 *
 */
public final class ConcreteArgumentsCollector implements ArgumentsCollector {

    /** Parent engine context associated with the supplier; immutable. */
    private final EngineContext engineContext;

    /**
     * Collected argument values.
     *
     * <p>This list is mutable and preserves insertion order.
     */
    private final List<Object> arguments = new ArrayList<>();

    /**
     * Configured per-class parallelism.
     *
     * <p>The value is mutable and is initialized to {@code max(1, availableProcessors)}.
     */
    private int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());

    /**
     * Creates a new supplier context.
     *
     * @param engineContext the engine context associated with the class; never {@code null}
     */
    public ConcreteArgumentsCollector(final @NonNull EngineContext engineContext) {
        this.engineContext = Objects.requireNonNull(engineContext, "engineContext must not be null");
    }

    @Override
    public EngineContext getEngineContext() {
        return engineContext;
    }

    @Override
    public ArgumentsCollector addArgument(final Object argument) {
        arguments.add(argument);
        return this;
    }

    @Override
    public ArgumentsCollector addArguments(final Object... arguments) {
        if (arguments == null) {
            return this;
        }
        this.arguments.addAll(Arrays.asList(arguments));
        return this;
    }

    @Override
    public ArgumentsCollector addArguments(final @NonNull List<?> arguments) {
        this.arguments.addAll(arguments);
        return this;
    }

    @Override
    public ArgumentsCollector setParallelism(final int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1");
        }
        this.parallelism = parallelism;
        return this;
    }

    /**
     * Returns collected argument values as an array.
     *
     * <p>The returned array preserves insertion order.
     *
     * @return collected argument values; never {@code null}
     */
    public Object[] toArray() {
        return arguments.toArray();
    }

    /**
     * Returns the configured per-class parallelism.
     *
     * <p>This method exists for engine integration. Suppliers should configure parallelism through
     * {@link #setParallelism(int)}.
     *
     * @return the configured parallelism; always {@code >= 1}
     */
    public int getParallelism() {
        return parallelism;
    }
}
