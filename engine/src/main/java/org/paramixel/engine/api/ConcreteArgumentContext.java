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

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Store;

/**
 * Concrete implementation of {@link ArgumentContext}.
 *
 * <p>This class provides the implementation details for argument-level context
 * information used during test method invocations.</p>
 *
 * @see ArgumentContext
 */
public final class ConcreteArgumentContext implements ArgumentContext {

    /**
     * Parent class context for this invocation.
     */
    private final ConcreteClassContext classContext;

    /**
     * Argument value for this invocation.
     */
    private final Object argument;

    /**
     * Zero-based index of this argument within the supplier output.
     */
    private final int argumentIndex;

    /**
     * Argument-scoped store.
     */
    private final Store store;

    /**
     * Creates a new ConcreteArgumentContext for the specified invocation.
     *
     * <p>This constructor initializes the context with the parent class context,
     * the argument for this invocation, and the invocation index.</p>
     *
     * @param classContext the parent class context; must not be null
     * @param argument the argument for this invocation; may be null
     * @param argumentIndex the zero-based index of this invocation in the test class
     * @throws NullPointerException if classContext is null
     */
    public ConcreteArgumentContext(
            final @NonNull ConcreteClassContext classContext, final Object argument, final int argumentIndex) {
        this.classContext = Objects.requireNonNull(classContext, "classContext must not be null");
        this.argument = argument;
        this.argumentIndex = argumentIndex;
        this.store = new ConcreteStore();
    }

    @Override
    public ClassContext getClassContext() {
        return classContext;
    }

    @Override
    public Object getArgument() {
        return argument;
    }

    @Override
    public int getArgumentIndex() {
        return argumentIndex;
    }

    @Override
    public Store getStore() {
        return store;
    }

    @Override
    public String toString() {
        return "ConcreteArgumentContext{" + "testClass="
                + classContext.getTestClassName() + ", invocationIndex="
                + argumentIndex + ", argument="
                + argument + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(classContext, argument, argumentIndex);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ConcreteArgumentContext other = (ConcreteArgumentContext) obj;
        return argumentIndex == other.argumentIndex
                && Objects.equals(classContext, other.classContext)
                && Objects.equals(argument, other.argument);
    }
}
