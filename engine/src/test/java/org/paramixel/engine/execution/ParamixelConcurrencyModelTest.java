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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;
import org.paramixel.engine.util.FastIdUtil;

public class ParamixelConcurrencyModelTest {

    @Test
    public void classProgresses_whenArgumentSlotsUnavailable_andFallsBackToInline() throws Exception {

        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final Map<Class<?>, ClassContext> classContexts = new ConcurrentHashMap<>();
        final Map<Class<?>, Object> testInstances = new ConcurrentHashMap<>();

        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor classDescriptor = new ParamixelTestClassDescriptor(
                rootId.append("class", SaturationTest.class.getName()),
                SaturationTest.class,
                SaturationTest.class.getName());
        classDescriptor.setArgumentParallelism(2);

        final ParamixelTestArgumentDescriptor arg0 = new ParamixelTestArgumentDescriptor(
                classDescriptor.getUniqueId().append("argument", "0"), 0, "a0", "argument:0");
        final ParamixelTestArgumentDescriptor arg1 = new ParamixelTestArgumentDescriptor(
                classDescriptor.getUniqueId().append("argument", "1"), 1, "a1", "argument:1");
        classDescriptor.addChild(arg0);
        classDescriptor.addChild(arg1);

        final Method m = SaturationTest.class.getDeclaredMethod("test", ArgumentContext.class);
        arg0.addChild(new ParamixelTestMethodDescriptor(arg0.getUniqueId().append("method", "test"), m, "test"));
        arg1.addChild(new ParamixelTestMethodDescriptor(arg1.getUniqueId().append("method", "test"), m, "test"));

        final CountDownLatch arg1Entered = new CountDownLatch(1);
        final CountDownLatch releaseArg1 = new CountDownLatch(1);
        SaturationTest.arg1Entered = arg1Entered;
        SaturationTest.releaseArg1 = releaseArg1;

        final RecordingListener listener = new RecordingListener();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(1)) {
            // Saturate the only argument slot + the remaining total slot.
            final ParamixelConcurrencyLimiter.ArgumentPermit saturationPermit =
                    runtime.limiter().tryAcquireArgumentExecution().orElseThrow();

            final ParamixelClassRunner runner =
                    new ParamixelClassRunner(runtime, engineContext, listener, classContexts, testInstances);
            final ParamixelConcurrencyLimiter.ClassPermit classPermit =
                    runtime.limiter().acquireClassExecution();
            final Future<?> future = runtime.submitNamed(FastIdUtil.getId(6), () -> {
                try (classPermit) {
                    runner.runTestClass(classDescriptor);
                }
            });

            assertThat(arg1Entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(runtime.limiter().argumentSlotsInUse()).isEqualTo(1);
            assertThat(runtime.limiter().totalSlotsInUse()).isEqualTo(2);

            releaseArg1.countDown();
            saturationPermit.close();
            future.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void globalConcurrencyNeverExceedsTwoTimesCores_underInvocationParallelism() throws Exception {

        final int cores = 1;
        final AtomicInteger running = new AtomicInteger(0);
        final AtomicInteger max = new AtomicInteger(0);

        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final Map<Class<?>, ClassContext> classContexts = new ConcurrentHashMap<>();
        final Map<Class<?>, Object> testInstances = new ConcurrentHashMap<>();

        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor classDescriptor = new ParamixelTestClassDescriptor(
                rootId.append("class", MaxConcurrencyTest.class.getName()),
                MaxConcurrencyTest.class,
                MaxConcurrencyTest.class.getName());
        classDescriptor.setArgumentParallelism(4);

        final ParamixelTestArgumentDescriptor arg0 = new ParamixelTestArgumentDescriptor(
                classDescriptor.getUniqueId().append("argument", "0"), 0, "arg", "argument:0");
        classDescriptor.addChild(arg0);

        for (String methodName : List.of("t1", "t2", "t3", "t4")) {
            final Method m = MaxConcurrencyTest.class.getDeclaredMethod(methodName, ArgumentContext.class);
            arg0.addChild(
                    new ParamixelTestMethodDescriptor(arg0.getUniqueId().append("method", methodName), m, methodName));
        }

        MaxConcurrencyTest.running = running;
        MaxConcurrencyTest.max = max;

        final RecordingListener listener = new RecordingListener();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(cores)) {
            final ParamixelClassRunner runner =
                    new ParamixelClassRunner(runtime, engineContext, listener, classContexts, testInstances);

            final ParamixelConcurrencyLimiter.ClassPermit permit =
                    runtime.limiter().acquireClassExecution();
            runtime.submitNamed(FastIdUtil.getId(6), () -> {
                        try (permit) {
                            runner.runTestClass(classDescriptor);
                        }
                    })
                    .get(10, TimeUnit.SECONDS);
        }

        assertThat(max.get()).isLessThanOrEqualTo(cores * 2);
        assertThat(max.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void multipleClassesRunConcurrently_upToCoreCount() throws Exception {

        final int cores = 2;
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final Map<Class<?>, ClassContext> classContexts = new ConcurrentHashMap<>();
        final Map<Class<?>, Object> testInstances = new ConcurrentHashMap<>();

        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor d1 = new ParamixelTestClassDescriptor(
                rootId.append("class", BlockingClassTest.class.getName() + "#1"), BlockingClassTest.class, "Blocking1");
        final ParamixelTestClassDescriptor d2 = new ParamixelTestClassDescriptor(
                rootId.append("class", BlockingClassTest.class.getName() + "#2"), BlockingClassTest.class, "Blocking2");
        d1.setArgumentParallelism(1);
        d2.setArgumentParallelism(1);

        final ParamixelTestArgumentDescriptor a1 =
                new ParamixelTestArgumentDescriptor(d1.getUniqueId().append("argument", "0"), 0, "arg", "argument:0");
        final ParamixelTestArgumentDescriptor a2 =
                new ParamixelTestArgumentDescriptor(d2.getUniqueId().append("argument", "0"), 0, "arg", "argument:0");
        d1.addChild(a1);
        d2.addChild(a2);

        final Method m = BlockingClassTest.class.getDeclaredMethod("test", ArgumentContext.class);
        a1.addChild(new ParamixelTestMethodDescriptor(a1.getUniqueId().append("method", "test"), m, "test"));
        a2.addChild(new ParamixelTestMethodDescriptor(a2.getUniqueId().append("method", "test"), m, "test"));

        final CountDownLatch entered = new CountDownLatch(cores);
        final CountDownLatch release = new CountDownLatch(1);
        BlockingClassTest.entered = entered;
        BlockingClassTest.release = release;

        final RecordingListener listener = new RecordingListener();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(cores)) {
            final ParamixelClassRunner runner =
                    new ParamixelClassRunner(runtime, engineContext, listener, classContexts, testInstances);

            final ParamixelConcurrencyLimiter.ClassPermit p1 = runtime.limiter().acquireClassExecution();
            final ParamixelConcurrencyLimiter.ClassPermit p2 = runtime.limiter().acquireClassExecution();

            final Future<?> f1 = runtime.submitNamed(FastIdUtil.getId(6), () -> {
                try (p1) {
                    runner.runTestClass(d1);
                }
            });
            final Future<?> f2 = runtime.submitNamed(FastIdUtil.getId(6), () -> {
                try (p2) {
                    runner.runTestClass(d2);
                }
            });

            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);
        }
    }

    public static final class SaturationTest {

        static volatile CountDownLatch arg1Entered;
        static volatile CountDownLatch releaseArg1;

        @Paramixel.ArgumentsCollector
        public static void arguments(final ArgumentsCollector collector) {
            collector.setParallelism(2);
            collector.addArguments("a0", "a1");
        }

        @Paramixel.Test
        public void test(final ArgumentContext context) throws Exception {
            if (context.getArgumentIndex() == 1) {
                arg1Entered.countDown();
                releaseArg1.await(5, TimeUnit.SECONDS);
            }
        }
    }

    public static final class MaxConcurrencyTest {

        static volatile AtomicInteger running;
        static volatile AtomicInteger max;

        @Paramixel.ArgumentsCollector
        public static void arguments(final ArgumentsCollector collector) {
            collector.setParallelism(4);
            collector.addArgument("arg");
        }

        @Paramixel.Test
        public void t1(final ArgumentContext context) throws Exception {
            doWork();
        }

        @Paramixel.Test
        public void t2(final ArgumentContext context) throws Exception {
            doWork();
        }

        @Paramixel.Test
        public void t3(final ArgumentContext context) throws Exception {
            doWork();
        }

        @Paramixel.Test
        public void t4(final ArgumentContext context) throws Exception {
            doWork();
        }

        private void doWork() throws Exception {
            final int now = running.incrementAndGet();
            max.accumulateAndGet(now, Math::max);
            Thread.sleep(50);
            running.decrementAndGet();
        }
    }

    public static final class BlockingClassTest {

        static volatile CountDownLatch entered;
        static volatile CountDownLatch release;

        @Paramixel.Test
        public void test(final ArgumentContext context) throws Exception {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingListener implements EngineExecutionListener {

        @Override
        public void executionFinished(
                final TestDescriptor testDescriptor, final TestExecutionResult testExecutionResult) {
            // no-op
        }
    }
}
