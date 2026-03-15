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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.api.ArgumentContext;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;
import org.paramixel.engine.invoker.ParamixelReflectionInvoker;
import org.paramixel.engine.util.LifecycleMethodUtil;

/**
 * Simple argument executor using per-argument lifecycle hooks.
 *
 * <p>Executes all arguments for a single test class with proper parallelism limiting
 * via Semaphore. Each argument runs BeforeAll/AfterAll hooks in its own ArgumentContext.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class SimpleArgumentExecutor {

    private final Class<?> testClass;

    private final Object testInstance;

    private final ConcreteClassContext classContext;

    private final EngineExecutionListener listener;

    private final ParamixelTestClassDescriptor classDescriptor;

    public SimpleArgumentExecutor(
            final @NonNull Class<?> testClass,
            final @Nullable Object testInstance,
            final @NonNull ConcreteClassContext classContext,
            final @NonNull EngineExecutionListener listener,
            final @NonNull ParamixelTestClassDescriptor classDescriptor) {
        this.testClass = Objects.requireNonNull(testClass, "testClass must not be null");
        this.testInstance = testInstance;
        this.classContext = Objects.requireNonNull(classContext, "classContext must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.classDescriptor = Objects.requireNonNull(classDescriptor, "classDescriptor must not be null");
    }

    public void execute() {
        final List<ParamixelTestArgumentDescriptor> argumentDescriptors = classDescriptor.getChildren().stream()
                .filter(d -> d instanceof ParamixelTestArgumentDescriptor)
                .map(d -> (ParamixelTestArgumentDescriptor) d)
                .sorted((a, b) -> Integer.compare(a.getArgumentIndex(), b.getArgumentIndex()))
                .toList();

        if (argumentDescriptors.isEmpty()) {
            executeSingleArgument(null, -1, null);
            return;
        }

        final int parallelism = classDescriptor.getArgumentParallelism();
        final ExecutorService executor = WorkerPoolManager.createExecutor(parallelism);
        final Semaphore semaphore = new Semaphore(parallelism, true);

        final List<Future<?>> futures = new ArrayList<>();

        for (ParamixelTestArgumentDescriptor argumentDescriptor : argumentDescriptors) {
            final int argumentIndex = argumentDescriptor.getArgumentIndex();
            final Object argument = argumentDescriptor.getArgument();

            futures.add(executor.submit(() -> {
                try {
                    semaphore.acquire();
                    try {
                        listener.executionStarted(argumentDescriptor);
                        try {
                            executeSingleArgument(argument, argumentIndex, argumentDescriptor);
                        } finally {
                            listener.executionFinished(argumentDescriptor, TestExecutionResult.successful());
                        }
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                classContext.recordFailure(e);
            }
        }

        executor.shutdown();
    }

    private void executeSingleArgument(
            final Object argument, final int argumentIndex, final ParamixelTestArgumentDescriptor argumentDescriptor) {
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(argument, argumentIndex);

        boolean beforeAllSucceeded = false;

        try {
            beforeAllSucceeded = runBeforeAll(argumentContext);

            if (beforeAllSucceeded) {
                runTestMethods(argumentContext, argumentDescriptor);
            }

            runAfterAll(argumentContext, beforeAllSucceeded);

        } catch (Throwable t) {
            classContext.recordFailure(t);
        } finally {
            closeAutoCloseable(argument);
        }
    }

    private void closeAutoCloseable(final Object argument) {
        if (argument instanceof AutoCloseable) {
            try {
                ((AutoCloseable) argument).close();
            } catch (Exception e) {
                classContext.recordFailure(e);
            }
        }
    }

    private void runTestMethods(
            final ArgumentContext argumentContext, final ParamixelTestArgumentDescriptor argumentDescriptor) {
        final List<ParamixelTestMethodDescriptor> methodDescriptors = collectTestMethodDescriptors(argumentDescriptor);

        for (ParamixelTestMethodDescriptor testMethodDesc : methodDescriptors) {
            listener.executionStarted(testMethodDesc);

            boolean beforeEachSucceeded = false;

            try {
                beforeEachSucceeded = runBeforeEach(argumentContext);

                if (beforeEachSucceeded) {
                    ParamixelReflectionInvoker.invokeTestMethod(
                            testMethodDesc.getTestMethod(), testInstance, argumentContext);
                }

                runAfterEach(argumentContext, beforeEachSucceeded);

            } catch (Throwable t) {
                classContext.recordFailure(t);
                listener.executionFinished(testMethodDesc, TestExecutionResult.failed(t));
                continue;
            }

            listener.executionFinished(testMethodDesc, TestExecutionResult.successful());
        }
    }

    private List<ParamixelTestMethodDescriptor> collectTestMethodDescriptors(
            final ParamixelTestArgumentDescriptor argumentDescriptor) {
        final List<ParamixelTestMethodDescriptor> descriptors = new ArrayList<>();

        if (argumentDescriptor != null) {
            for (org.junit.platform.engine.TestDescriptor child : argumentDescriptor.getChildren()) {
                if (child instanceof ParamixelTestMethodDescriptor) {
                    descriptors.add((ParamixelTestMethodDescriptor) child);
                }
            }
        } else {
            for (org.junit.platform.engine.TestDescriptor classChild : classDescriptor.getChildren()) {
                if (classChild instanceof ParamixelTestArgumentDescriptor) {
                    final ParamixelTestArgumentDescriptor argDesc = (ParamixelTestArgumentDescriptor) classChild;
                    for (org.junit.platform.engine.TestDescriptor child : argDesc.getChildren()) {
                        if (child instanceof ParamixelTestMethodDescriptor) {
                            descriptors.add((ParamixelTestMethodDescriptor) child);
                        }
                    }
                }
            }
        }

        descriptors.sort(Comparator.comparingInt((ParamixelTestMethodDescriptor d) -> getOrder(d.getTestMethod()))
                .thenComparing(d -> d.getTestMethod().getName()));
        return List.copyOf(descriptors);
    }

    private int getOrder(final Method method) {
        final org.paramixel.api.Paramixel.Order order = method.getAnnotation(org.paramixel.api.Paramixel.Order.class);
        return order != null ? order.value() : Integer.MAX_VALUE;
    }

    private boolean runBeforeAll(final ArgumentContext argumentContext) {
        final List<Method> methods =
                LifecycleMethodUtil.getLifecycleMethods(testClass, org.paramixel.api.Paramixel.BeforeAll.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeBeforeAll(method, testInstance, argumentContext);
            } catch (Throwable t) {
                classContext.recordFailure(t);
                return false;
            }
        }
        return true;
    }

    private boolean runBeforeEach(final ArgumentContext argumentContext) {
        final List<Method> methods =
                LifecycleMethodUtil.getLifecycleMethods(testClass, org.paramixel.api.Paramixel.BeforeEach.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeBeforeEach(method, testInstance, argumentContext);
            } catch (Throwable t) {
                classContext.recordFailure(t);
                return false;
            }
        }
        return true;
    }

    private void runAfterEach(final ArgumentContext argumentContext, final boolean beforeEachSucceeded) {
        if (!beforeEachSucceeded) {
            return;
        }
        final List<Method> methods =
                LifecycleMethodUtil.getLifecycleMethods(testClass, org.paramixel.api.Paramixel.AfterEach.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeAfterEach(method, testInstance, argumentContext);
            } catch (Throwable t) {
                classContext.recordFailure(t);
            }
        }
    }

    private void runAfterAll(final ArgumentContext argumentContext, final boolean beforeAllSucceeded) {
        if (!beforeAllSucceeded) {
            return;
        }
        final List<Method> methods =
                LifecycleMethodUtil.getLifecycleMethods(testClass, org.paramixel.api.Paramixel.AfterAll.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeAfterAll(method, testInstance, argumentContext);
            } catch (Throwable t) {
                classContext.recordFailure(t);
            }
        }
    }
}
