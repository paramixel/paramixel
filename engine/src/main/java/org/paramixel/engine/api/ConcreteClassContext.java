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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ClassContext;
import org.paramixel.api.EngineContext;
import org.paramixel.api.Store;

/**
 * Concrete implementation of {@link ClassContext}.
 *
 * <p>This class provides the implementation details for class-level context
 * information used during test class execution, including state tracking for
 * invocations, successes, failures, and errors.</p>
 *
 * @see ClassContext
 */
public final class ConcreteClassContext implements ClassContext {

    /**
     * The test class associated with this context.
     */
    private final Class<?> testClass;

    /**
     * The parent engine context.
     */
    private final EngineContext engineContext;

    /**
     * The instantiated test object, if available.
     */
    private final Object testInstance;

    /**
     * Counter for total test invocations executed.
     */
    private final AtomicInteger invocationCount;

    /**
     * Counter for successful test invocations.
     */
    private final AtomicInteger successCount;

    /**
     * Counter for failed test invocations.
     */
    private final AtomicInteger failureCount;

    /**
     * First failure recorded during execution, if any.
     */
    private final AtomicReference<Throwable> firstFailure;

    /**
     * Class-scoped store.
     */
    private final Store store;

    /**
     * Thread-safe cache of argument contexts keyed by argument index.
     */
    private final Map<Integer, ConcreteArgumentContext> argumentContexts;

    /**
     * Creates a new ConcreteClassContext for the specified test class and engine context.
     *
     * <p>This constructor initializes all state tracking fields and validates the parameters.</p>
     *
     * @param testClass the test class being executed; must not be null
     * @param engineContext the parent engine context; must not be null
     * @param testInstance the instantiated test object; may be null if not yet instantiated
     * @throws NullPointerException if testClass or engineContext is null
     */
    public ConcreteClassContext(
            final @NonNull Class<?> testClass, final @NonNull EngineContext engineContext, final Object testInstance) {
        this.testClass = Objects.requireNonNull(testClass, "testClass must not be null");
        this.engineContext = Objects.requireNonNull(engineContext, "engineContext must not be null");
        this.testInstance = testInstance;
        this.invocationCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.failureCount = new AtomicInteger(0);
        this.firstFailure = new AtomicReference<>(null);
        this.store = new ConcreteStore();
        this.argumentContexts = new ConcurrentHashMap<>();
    }

    @Override
    public Class<?> getTestClass() {
        return testClass;
    }

    @Override
    public EngineContext getEngineContext() {
        return engineContext;
    }

    @Override
    public Object getTestInstance() {
        return testInstance;
    }

    @Override
    public Store getStore() {
        return store;
    }

    /**
     * Returns the argument context for the given index, creating it if absent.
     *
     * @param argument the argument for this invocation; may be null
     * @param argumentIndex the zero-based index of this invocation in the test class
     * @return the argument context for this index
     */
    public ConcreteArgumentContext getOrCreateArgumentContext(final Object argument, final int argumentIndex) {
        return argumentContexts.computeIfAbsent(
                argumentIndex, index -> new ConcreteArgumentContext(this, argument, index));
    }

    /**
     * Removes the cached argument context for the given index.
     *
     * @param argumentIndex the zero-based index of this invocation in the test class
     * @return the removed argument context, or null if none existed
     */
    public ConcreteArgumentContext removeArgumentContext(final int argumentIndex) {
        return argumentContexts.remove(argumentIndex);
    }

    /**
     * Returns the fully qualified name of the test class.
     *
     * @return the fully qualified class name; never null
     */
    public String getTestClassName() {
        return testClass.getName();
    }

    /**
     * Increments and returns the current invocation count.
     *
     * <p>This method atomically increments the invocation counter and returns the new value.</p>
     *
     * @return the new invocation count after incrementing
     */
    public int incrementInvocationCount() {
        return invocationCount.incrementAndGet();
    }

    /**
     * Increments and returns the current success count.
     *
     * <p>This method atomically increments the success counter and returns the new value.</p>
     *
     * @return the new success count after incrementing
     */
    public int incrementSuccessCount() {
        return successCount.incrementAndGet();
    }

    /**
     * Increments and returns the current failure count.
     *
     * <p>This method atomically increments the failure counter and returns the new value.</p>
     *
     * @return the new failure count after incrementing
     */
    public int incrementFailureCount() {
        return failureCount.incrementAndGet();
    }

    /**
     * Records a test failure.
     *
     * <p>This method atomically records the first failure encountered during test execution.
     * Subsequent failures do not overwrite the first failure.</p>
     *
     * @param failure the Throwable that caused the test failure; must not be null
     */
    public void recordFailure(final @NonNull Throwable failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        firstFailure.compareAndSet(null, failure);
    }

    /**
     * Returns the first failure that was recorded during test execution.
     *
     * @return the first failure, or null if no failures occurred
     */
    public Throwable getFirstFailure() {
        return firstFailure.get();
    }

    @Override
    public String toString() {
        return "ConcreteClassContext{" + "testClass="
                + testClass.getName() + ", invocationCount="
                + invocationCount.get() + ", successCount="
                + successCount.get() + ", failureCount="
                + failureCount.get() + '}';
    }
}
