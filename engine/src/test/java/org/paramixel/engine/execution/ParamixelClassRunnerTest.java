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

package org.paramixel.engine.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;
import org.paramixel.engine.util.FastId;

public class ParamixelClassRunnerTest {

    @Test
    public void executesLifecycleMethods_andClosesArgumentAndTestInstance() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor classDescriptor = new ParamixelTestClassDescriptor(
                rootId.append("class", SuccessfulLifecycleTest.class.getName()),
                SuccessfulLifecycleTest.class,
                SuccessfulLifecycleTest.class.getName());
        classDescriptor.setArgumentParallelism(1);

        final CloseableArgument arg = new CloseableArgument();
        final ParamixelTestArgumentDescriptor argDescriptor = new ParamixelTestArgumentDescriptor(
                classDescriptor.getUniqueId().append("argument", "0"), 0, arg, "argument:0");
        classDescriptor.addChild(argDescriptor);

        final Method testMethod = SuccessfulLifecycleTest.class.getDeclaredMethod("test", ArgumentContext.class);
        argDescriptor.addChild(new ParamixelTestMethodDescriptor(
                argDescriptor.getUniqueId().append("method", "test"), testMethod, "test"));

        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final Map<Class<?>, ClassContext> classContexts = new ConcurrentHashMap<>();
        final Map<Class<?>, Object> testInstances = new ConcurrentHashMap<>();

        final RecordingListener listener = new RecordingListener();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(1)) {
            final ParamixelClassRunner runner =
                    new ParamixelClassRunner(runtime, engineContext, listener, classContexts, testInstances);

            final ParamixelConcurrencyLimiter.ClassPermit permit =
                    runtime.limiter().acquireClassExecution();
            runtime.submitNamed(FastId.getId(6), () -> {
                        try (permit) {
                            runner.runTestClass(classDescriptor);
                        }
                    })
                    .get();
        }

        assertThat(arg.closed.get()).isTrue();

        final SuccessfulLifecycleTest instance =
                (SuccessfulLifecycleTest) testInstances.get(SuccessfulLifecycleTest.class);
        assertThat(instance).isNotNull();
        assertThat(instance.closed.get()).isTrue();
        assertThat(instance.calls)
                .containsSequence(
                        "initialize", "beforeAll", "beforeEach", "test", "afterEach", "afterAll", "finalize", "close");

        final ConcreteClassContext classContext =
                (ConcreteClassContext) classContexts.get(SuccessfulLifecycleTest.class);
        assertThat(classContext).isNotNull();
        assertThat(classContext.getFirstFailure()).isNull();
        assertThat(classContext.removeArgumentContext(0)).isNull();
    }

    @Test
    public void runsAfterEachAfterAllAndFinalize_evenWhenTestFails() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor classDescriptor = new ParamixelTestClassDescriptor(
                rootId.append("class", FailingTest.class.getName()), FailingTest.class, FailingTest.class.getName());
        classDescriptor.setArgumentParallelism(1);

        final ParamixelTestArgumentDescriptor argDescriptor = new ParamixelTestArgumentDescriptor(
                classDescriptor.getUniqueId().append("argument", "0"), 0, null, "argument:0");
        classDescriptor.addChild(argDescriptor);

        final Method testMethod = FailingTest.class.getDeclaredMethod("test", ArgumentContext.class);
        argDescriptor.addChild(new ParamixelTestMethodDescriptor(
                argDescriptor.getUniqueId().append("method", "test"), testMethod, "test"));

        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final Map<Class<?>, ClassContext> classContexts = new ConcurrentHashMap<>();
        final Map<Class<?>, Object> testInstances = new ConcurrentHashMap<>();

        final RecordingListener listener = new RecordingListener();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(1)) {
            final ParamixelClassRunner runner =
                    new ParamixelClassRunner(runtime, engineContext, listener, classContexts, testInstances);
            final ParamixelConcurrencyLimiter.ClassPermit permit =
                    runtime.limiter().acquireClassExecution();
            runtime.submitNamed(FastId.getId(6), () -> {
                        try (permit) {
                            runner.runTestClass(classDescriptor);
                        }
                    })
                    .get();
        }

        final FailingTest instance = (FailingTest) testInstances.get(FailingTest.class);
        assertThat(instance.calls).contains("afterEach");
        assertThat(instance.calls).contains("afterAll");
        assertThat(instance.calls).contains("finalize");
        assertThat(instance.closed.get()).isTrue();

        final ConcreteClassContext classContext = (ConcreteClassContext) classContexts.get(FailingTest.class);
        assertThat(classContext.getFirstFailure()).isNotNull();
    }

    public static final class CloseableArgument implements AutoCloseable {

        final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void close() {
            closed.set(true);
        }
    }

    public static class SuccessfulLifecycleTest implements AutoCloseable {

        final List<String> calls = new CopyOnWriteArrayList<>();
        final AtomicBoolean closed = new AtomicBoolean(false);

        @Paramixel.Initialize
        public void initialize(final ClassContext context) {
            calls.add("initialize");
        }

        @Paramixel.BeforeAll
        public void beforeAll(final ArgumentContext context) {
            calls.add("beforeAll");
        }

        @Paramixel.BeforeEach
        public void beforeEach(final ArgumentContext context) {
            calls.add("beforeEach");
        }

        @Paramixel.Test
        public void test(final ArgumentContext context) {
            calls.add("test");
        }

        @Paramixel.AfterEach
        public void afterEach(final ArgumentContext context) {
            calls.add("afterEach");
        }

        @Paramixel.AfterAll
        public void afterAll(final ArgumentContext context) {
            calls.add("afterAll");
        }

        @Paramixel.Finalize
        public void finalizeClass(final ClassContext context) {
            calls.add("finalize");
        }

        @Override
        public void close() {
            closed.set(true);
            calls.add("close");
        }
    }

    public static class FailingTest implements AutoCloseable {

        final List<String> calls = new CopyOnWriteArrayList<>();
        final AtomicBoolean closed = new AtomicBoolean(false);

        @Paramixel.BeforeAll
        public void beforeAll(final ArgumentContext context) {
            calls.add("beforeAll");
        }

        @Paramixel.BeforeEach
        public void beforeEach(final ArgumentContext context) {
            calls.add("beforeEach");
        }

        @Paramixel.Test
        public void test(final ArgumentContext context) {
            calls.add("test");
            throw new RuntimeException("boom");
        }

        @Paramixel.AfterEach
        public void afterEach(final ArgumentContext context) {
            calls.add("afterEach");
        }

        @Paramixel.AfterAll
        public void afterAll(final ArgumentContext context) {
            calls.add("afterAll");
        }

        @Paramixel.Finalize
        public void finalizeClass(final ClassContext context) {
            calls.add("finalize");
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class RecordingListener implements EngineExecutionListener {

        @Override
        public void executionFinished(
                final TestDescriptor testDescriptor, final TestExecutionResult testExecutionResult) {
            // no-op; we validate side effects on contexts/instances
        }
    }
}
