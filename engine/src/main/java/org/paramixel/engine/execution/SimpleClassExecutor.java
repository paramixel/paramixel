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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestExecutionResult;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.invoker.ParamixelReflectionInvoker;
import org.paramixel.engine.util.LifecycleMethodUtil;

/**
 * Simple test class executor using virtual threads.
 *
 * <p>This executor submits each test class to a thread pool
 * sized to the engine's class parallelism setting.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class SimpleClassExecutor implements AutoCloseable {

    private final ExecutorService executor;

    private final ConcreteEngineContext engineContext;

    private final EngineExecutionListener listener;

    private volatile Throwable firstFailure;

    public SimpleClassExecutor(
            final @NonNull ConcreteEngineContext engineContext, final @NonNull EngineExecutionListener listener) {
        this.engineContext = Objects.requireNonNull(engineContext, "engineContext must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        final int parallelism = engineContext.getClassParallelism();
        this.executor = WorkerPoolManager.createExecutor(parallelism);
    }

    public void executeAll(final @NonNull List<ParamixelTestClassDescriptor> classDescriptors) {
        Objects.requireNonNull(classDescriptors, "classDescriptors must not be null");

        final List<Future<?>> futures = new ArrayList<>();

        for (ParamixelTestClassDescriptor classDescriptor : classDescriptors) {
            final Future<?> future = executor.submit(() -> {
                try {
                    executeSingleClass(classDescriptor);
                } catch (Throwable t) {
                    listener.executionFinished(classDescriptor, TestExecutionResult.failed(t));
                    if (firstFailure == null) {
                        firstFailure = t;
                    }
                }
            });
            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (firstFailure != null) {
            throw new RuntimeException(firstFailure);
        }
    }

    public Throwable getFirstFailure() {
        return firstFailure;
    }

    private void executeSingleClass(final ParamixelTestClassDescriptor classDescriptor) {
        final Class<?> testClass = classDescriptor.getTestClass();
        listener.executionStarted(classDescriptor);

        Object testInstance = null;
        ConcreteClassContext classContext = null;

        try {
            testInstance = testClass.getDeclaredConstructor().newInstance();
            classContext = new ConcreteClassContext(testClass, engineContext, testInstance);

            if (runInitialize(testClass, testInstance, classContext)) {
                new SimpleArgumentExecutor(testClass, testInstance, classContext, listener, classDescriptor).execute();
            }

            runFinalize(testClass, testInstance, classContext);

        } catch (Throwable t) {
            if (classContext != null) {
                classContext.recordFailure(t);
            }
        } finally {
            if (testInstance instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) testInstance).close();
                } catch (Exception ignored) {
                }
            }

            final Throwable firstFailure = classContext != null ? classContext.getFirstFailure() : null;
            final TestExecutionResult result =
                    firstFailure != null ? TestExecutionResult.failed(firstFailure) : TestExecutionResult.successful();
            listener.executionFinished(classDescriptor, result);

            if (firstFailure != null && SimpleClassExecutor.this.firstFailure == null) {
                SimpleClassExecutor.this.firstFailure = firstFailure;
            }
        }
    }

    private boolean runInitialize(
            final Class<?> testClass, final @Nullable Object testInstance, final ConcreteClassContext classContext) {
        final List<Method> methods =
                LifecycleMethodUtil.getLifecycleMethods(testClass, org.paramixel.api.Paramixel.Initialize.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeInitialize(method, testInstance, classContext);
            } catch (Throwable t) {
                classContext.recordFailure(t);
                return false;
            }
        }
        return true;
    }

    private void runFinalize(
            final Class<?> testClass, final @Nullable Object testInstance, final ConcreteClassContext classContext) {
        final List<Method> methods =
                LifecycleMethodUtil.getLifecycleMethods(testClass, org.paramixel.api.Paramixel.Finalize.class);
        for (Method method : methods) {
            try {
                ParamixelReflectionInvoker.invokeFinalize(method, testInstance, classContext);
            } catch (Throwable t) {
                classContext.recordFailure(t);
            }
        }
    }

    public void close() {
        executor.shutdown();
    }
}
