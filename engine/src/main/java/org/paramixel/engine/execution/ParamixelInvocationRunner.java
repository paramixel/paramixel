/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.execution;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;
import org.paramixel.engine.invoker.ParamixelReflectionInvoker;
import org.paramixel.engine.util.FastIdUtil;

/**
 * Executes {@link Paramixel.Test} method invocations for one argument bucket.
 *
 * <p>This runner executes method-level lifecycle hooks ({@link Paramixel.BeforeEach} and
 * {@link Paramixel.AfterEach}) and invokes test methods via
 * {@link ParamixelReflectionInvoker#invokeTestMethod(Method, Object, ArgumentContext)}.
 *
 * <p><b>Ordering</b>
 * <p>When any discovered method has {@link Paramixel.Order}, this runner executes methods
 * sequentially in descriptor order, even when parallelism is configured.
 *
 * <p><b>Thread safety</b>
 * <p>This class is not thread-safe. It is constructed per argument bucket and is intended for
 * single-threaded use by {@link ParamixelClassRunner}.
 *
 */
public final class ParamixelInvocationRunner {

    /** Logger used for lifecycle and invocation diagnostics. */
    private static final Logger LOGGER = Logger.getLogger(ParamixelInvocationRunner.class.getName());

    /**
     * Cache of lifecycle methods by (class, annotation, order).
     *
     * <p>This cache is static to avoid repeated reflection scanning across invocations.
     */
    private static final ConcurrentHashMap<LifecycleCacheKey, List<Method>> LIFECYCLE_METHOD_CACHE =
            new ConcurrentHashMap<>();

    /** Shared execution runtime used for task submission and permits. */
    private final ParamixelExecutionRuntime runtime;

    /** Listener notified for method descriptor start/finish events. */
    private final EngineExecutionListener listener;

    /** Owning class context used for metrics and failure aggregation. */
    private final ConcreteClassContext classContext;

    /** Instantiated test instance used to invoke lifecycle and test methods. */
    private final Object testInstance;

    /**
     * Creates an invocation runner for a single argument bucket.
     *
     * @param runtime the shared runtime; never {@code null}
     * @param listener the listener to notify; never {@code null}
     * @param classContext the owning class context; never {@code null}
     * @param testInstance the instantiated test object; never {@code null}
     */
    public ParamixelInvocationRunner(
            final ParamixelExecutionRuntime runtime,
            final EngineExecutionListener listener,
            final ConcreteClassContext classContext,
            final Object testInstance) {
        this.runtime = runtime;
        this.listener = listener;
        this.classContext = classContext;
        this.testInstance = testInstance;
    }

    /**
     * Executes test method descriptors associated with a single argument index.
     *
     * <p>This method identifies {@link ParamixelTestMethodDescriptor} instances under the provided
     * {@link ParamixelTestClassDescriptor} that correspond to {@code argumentIndex}. It then
     * executes them either sequentially or concurrently depending on:
     * <ul>
     *   <li>configured argument parallelism ({@link ParamixelTestClassDescriptor#getArgumentParallelism()})</li>
     *   <li>presence of {@link Paramixel.Order}</li>
     *   <li>number of methods</li>
     * </ul>
     *
     * <p><b>Failure handling</b>
     * <p>This method returns the first observed failure (if any) as the bucket result.
     *
     * @param classDescriptor the class descriptor to execute; never {@code null}
     * @param argument the argument value; may be {@code null}
     * @param argumentIndex the argument index
     * @return the aggregated result for the bucket; never {@code null}
     */
    public TestExecutionResult runInvocations(
            final ParamixelTestClassDescriptor classDescriptor, final Object argument, final int argumentIndex) {
        final Class<?> testClass = classDescriptor.getTestClass();

        final List<ParamixelTestMethodDescriptor> methodDescriptors = classDescriptor.getChildren().stream()
                .filter(d -> d instanceof ParamixelTestArgumentDescriptor)
                .map(d -> (ParamixelTestArgumentDescriptor) d)
                .filter(d -> d.getArgumentIndex() == argumentIndex)
                .flatMap(d -> d.getChildren().stream())
                .filter(d -> d instanceof ParamixelTestMethodDescriptor)
                .map(d -> (ParamixelTestMethodDescriptor) d)
                .toList();

        final int parallelism = classDescriptor.getArgumentParallelism();
        final boolean hasOrderedTests = methodDescriptors.stream()
                .anyMatch(descriptor -> descriptor.getTestMethod().isAnnotationPresent(Paramixel.Order.class));

        if (parallelism <= 1 || hasOrderedTests || methodDescriptors.size() <= 1) {
            Throwable firstFailure = null;
            for (ParamixelTestMethodDescriptor methodDescriptor : methodDescriptors) {
                final Method testMethod = methodDescriptor.getTestMethod();
                final TestExecutionResult result =
                        executeInvocation(methodDescriptor, testMethod, argument, argumentIndex);
                if (result.getStatus() == TestExecutionResult.Status.FAILED && firstFailure == null) {
                    firstFailure = result.getThrowable().orElse(null);
                }
            }
            return firstFailure == null ? TestExecutionResult.successful() : TestExecutionResult.failed(firstFailure);
        }

        final AtomicReference<Throwable> firstFailure = new AtomicReference<>(null);

        final int maxAsyncInvocations = Math.max(0, parallelism - 1);
        int asyncStarted = 0;
        final List<Future<TestExecutionResult>> futures = new ArrayList<>();

        // Schedule remaining invocations first (never blocking), then execute the first inline.
        for (int i = 1; i < methodDescriptors.size(); i++) {
            final ParamixelTestMethodDescriptor methodDescriptor = methodDescriptors.get(i);
            final Method testMethod = methodDescriptor.getTestMethod();

            if (asyncStarted < maxAsyncInvocations) {
                final Optional<ParamixelConcurrencyLimiter.ArgumentPermit> permitOpt =
                        runtime.limiter().tryAcquireArgumentExecution();
                if (permitOpt.isPresent()) {
                    asyncStarted++;
                    final ParamixelConcurrencyLimiter.ArgumentPermit permit = permitOpt.get();
                    futures.add(runtime.submitNamed(FastIdUtil.getId(6), () -> {
                        try (permit) {
                            return executeInvocation(methodDescriptor, testMethod, argument, argumentIndex);
                        }
                    }));
                    continue;
                }
            }

            final TestExecutionResult result = executeInvocation(methodDescriptor, testMethod, argument, argumentIndex);
            if (result.getStatus() == TestExecutionResult.Status.FAILED && firstFailure.get() == null) {
                firstFailure.set(result.getThrowable().orElse(null));
            }
        }

        final ParamixelTestMethodDescriptor firstDescriptor = methodDescriptors.get(0);
        final TestExecutionResult firstResult =
                executeInvocation(firstDescriptor, firstDescriptor.getTestMethod(), argument, argumentIndex);
        if (firstResult.getStatus() == TestExecutionResult.Status.FAILED && firstFailure.get() == null) {
            firstFailure.set(firstResult.getThrowable().orElse(null));
        }

        for (Future<TestExecutionResult> future : futures) {
            try {
                final TestExecutionResult result = future.get();
                if (result.getStatus() == TestExecutionResult.Status.FAILED && firstFailure.get() == null) {
                    firstFailure.set(result.getThrowable().orElse(null));
                }
            } catch (Exception e) {
                if (firstFailure.get() == null) {
                    firstFailure.set(e);
                }
            }
        }

        return firstFailure.get() == null
                ? TestExecutionResult.successful()
                : TestExecutionResult.failed(firstFailure.get());
    }

    /**
     * Executes a single test method invocation and reports the result.
     *
     * <p>This method notifies {@code listener} of execution start/finish, executes
     * {@link Paramixel.BeforeEach} and {@link Paramixel.AfterEach}, and invokes the test method
     * when before-each succeeds.
     *
     * <p><b>Side effects</b>
     * <ul>
     *   <li>Increments invocation/success/failure counters on {@code classContext}.</li>
     *   <li>Records any throwable on {@code classContext}.</li>
     * </ul>
     *
     * @param methodDescriptor the descriptor to report; never {@code null}
     * @param testMethod the reflective method to invoke; never {@code null}
     * @param argument the argument value; may be {@code null}
     * @param argumentIndex the argument index
     * @return the invocation result; never {@code null}
     */
    private TestExecutionResult executeInvocation(
            final ParamixelTestMethodDescriptor methodDescriptor,
            final Method testMethod,
            final Object argument,
            final int argumentIndex) {
        final Class<?> testClass = classContext.getTestClass();
        listener.executionStarted(methodDescriptor);

        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);

        Throwable invocationFailure = null;
        boolean invocationFailed = false;

        final Throwable beforeEachFailure = runBeforeEach(testClass, argumentContext);
        final boolean beforeEachSucceeded = beforeEachFailure == null;

        TestExecutionResult result = TestExecutionResult.successful();
        try {
            if (beforeEachSucceeded) {
                ParamixelReflectionInvoker.invokeTestMethod(testMethod, testInstance, argumentContext);
                classContext.incrementSuccessCount();
            } else {
                invocationFailure = beforeEachFailure;
                invocationFailed = true;
                classContext.incrementFailureCount();
                result = TestExecutionResult.failed(beforeEachFailure);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Test invocation failed: " + methodDescriptor.getUniqueId(), t);
            classContext.recordFailure(t);
            invocationFailure = t;
            if (!invocationFailed) {
                classContext.incrementFailureCount();
                invocationFailed = true;
            }
            result = TestExecutionResult.failed(t);
        } finally {
            classContext.incrementInvocationCount();

            final Throwable afterEachFailure = runAfterEach(testClass, argumentContext);
            if (afterEachFailure != null && invocationFailure == null) {
                invocationFailure = afterEachFailure;
                if (!invocationFailed) {
                    classContext.incrementFailureCount();
                }
                result = TestExecutionResult.failed(afterEachFailure);
            }

            listener.executionFinished(methodDescriptor, result);
        }

        if (invocationFailure != null) {
            classContext.recordFailure(invocationFailure);
        }

        return result;
    }

    /**
     * Executes {@link Paramixel.BeforeEach} lifecycle methods.
     *
     * <p>This method aborts on the first failure.
     *
     * @param testClass the test class; never {@code null}
     * @param argumentContext the argument context passed to hooks; never {@code null}
     * @return the first failure, or {@code null} when all hooks succeed
     */
    private Throwable runBeforeEach(final Class<?> testClass, final ArgumentContext argumentContext) {
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.BeforeEach.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeBeforeEach(method, testInstance, argumentContext);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "@Paramixel.BeforeEach failed", t);
                classContext.recordFailure(t);
                return t;
            }
        }
        return null;
    }

    /**
     * Executes {@link Paramixel.AfterEach} lifecycle methods.
     *
     * <p>This method executes all hooks and returns the first failure encountered.
     *
     * @param testClass the test class; never {@code null}
     * @param argumentContext the argument context passed to hooks; never {@code null}
     * @return the first failure, or {@code null} when all hooks succeed
     */
    private Throwable runAfterEach(final Class<?> testClass, final ArgumentContext argumentContext) {
        final List<Method> methods = getLifecycleMethods(testClass, Paramixel.AfterEach.class);
        Throwable firstFailure = null;
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeAfterEach(method, testInstance, argumentContext);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "@Paramixel.AfterEach failed", t);
                classContext.recordFailure(t);
                if (firstFailure == null) {
                    firstFailure = t;
                }
            }
        }
        return firstFailure;
    }

    /**
     * Returns lifecycle methods annotated with the given annotation.
     *
     * <p>The returned list is cached and immutable.
     *
     * @param testClass the root test class; never {@code null}
     * @param annotationType the lifecycle annotation type to match; never {@code null}
     * @return an immutable list of lifecycle methods; never {@code null}
     */
    private List<Method> getLifecycleMethods(
            final Class<?> testClass, final Class<? extends Annotation> annotationType) {
        final LifecycleCacheKey cacheKey = new LifecycleCacheKey(testClass, annotationType);
        return LIFECYCLE_METHOD_CACHE.computeIfAbsent(cacheKey, key -> {
            final Map<String, Method> bySignature = new ConcurrentHashMap<>();
            for (Class<?> current = testClass;
                    current != null && current != Object.class;
                    current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(annotationType)) {
                        continue;
                    }
                    if (method.isAnnotationPresent(Paramixel.Disabled.class)) {
                        continue;
                    }

                    final String signatureKey = signatureKey(method);
                    bySignature.putIfAbsent(signatureKey, method);
                }
            }

            final List<Method> methods = new ArrayList<>(bySignature.values());
            methods.sort(Comparator.comparingInt(ParamixelInvocationRunner::getOrderValue)
                    .thenComparing(Method::getName));
            return Collections.unmodifiableList(methods);
        });
    }

    private static int getOrderValue(final @NonNull Method method) {
        final Paramixel.Order order = method.getAnnotation(Paramixel.Order.class);
        if (order == null) {
            return Integer.MAX_VALUE;
        }
        return order.value();
    }

    private static String signatureKey(final @NonNull Method method) {
        final StringBuilder builder = new StringBuilder();
        builder.append(method.getName());
        builder.append('(');
        final Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(parameterTypes[i].getName());
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * Cache key for {@link #LIFECYCLE_METHOD_CACHE}.
     *
     * <p>This type is private because it is an internal memoization detail.
     */
    private static final class LifecycleCacheKey {

        /** Root test class for the lifecycle scan; immutable. */
        private final Class<?> testClass;

        /** Lifecycle annotation type used for matching; immutable. */
        private final Class<? extends Annotation> annotationType;

        /**
         * Creates a cache key.
         *
         * @param testClass the test class; never {@code null}
         * @param annotationType the lifecycle annotation type; never {@code null}
         */
        private LifecycleCacheKey(final Class<?> testClass, final Class<? extends Annotation> annotationType) {
            this.testClass = testClass;
            this.annotationType = annotationType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LifecycleCacheKey)) {
                return false;
            }
            final LifecycleCacheKey that = (LifecycleCacheKey) o;
            return testClass.equals(that.testClass) && annotationType.equals(that.annotationType);
        }

        @Override
        public int hashCode() {
            int result = testClass.hashCode();
            result = 31 * result + annotationType.hashCode();
            return result;
        }
    }
}
