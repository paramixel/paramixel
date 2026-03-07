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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

public class ParamixelInvocationRunnerTest {

    @Test
    public void executesInvocationsInParallelWhenParallelismGreaterThanOne_andNoOrderedTests() throws Exception {
        final int invocations = 4;
        final CountDownLatch ready = new CountDownLatch(invocations);
        final CountDownLatch start = new CountDownLatch(1);
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        final AtomicInteger concurrent = new AtomicInteger(0);
        final AtomicInteger maxConcurrent = new AtomicInteger(0);

        final ParallelInvocationTest instance =
                new ParallelInvocationTest(ready, start, threads, concurrent, maxConcurrent);
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext =
                new ConcreteClassContext(ParallelInvocationTest.class, engineContext, instance);

        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor classDescriptor = new ParamixelTestClassDescriptor(
                rootId.append("class", ParallelInvocationTest.class.getName()),
                ParallelInvocationTest.class,
                ParallelInvocationTest.class.getName());
        classDescriptor.setArgumentParallelism(4);
        final ParamixelTestArgumentDescriptor argDescriptor = new ParamixelTestArgumentDescriptor(
                classDescriptor.getUniqueId().append("argument", "0"), 0, "arg", "argument:0");
        classDescriptor.addChild(argDescriptor);

        for (String methodName : List.of("t1", "t2", "t3", "t4")) {
            final Method m = ParallelInvocationTest.class.getDeclaredMethod(methodName, ArgumentContext.class);
            argDescriptor.addChild(new ParamixelTestMethodDescriptor(
                    argDescriptor.getUniqueId().append("method", methodName), m, methodName));
        }

        final RecordingListener listener = new RecordingListener();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(4)) {
            final ParamixelInvocationRunner runner =
                    new ParamixelInvocationRunner(runtime, listener, classContext, instance);

            final var future =
                    runtime.submitNamed("invocation-root", () -> runner.runInvocations(classDescriptor, "arg", 0));

            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            final TestExecutionResult result = (TestExecutionResult) future.get(15, TimeUnit.SECONDS);
            assertThat(result.getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        }

        assertThat(maxConcurrent.get()).isGreaterThanOrEqualTo(2);
        assertThat(threads.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    public void runsSequentiallyWhenAnyTestMethodHasOrderAnnotation_evenIfParallelismGreaterThanOne() throws Exception {
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        final AtomicInteger concurrent = new AtomicInteger(0);
        final AtomicInteger maxConcurrent = new AtomicInteger(0);

        final OrderedInvocationTest instance = new OrderedInvocationTest(threads, concurrent, maxConcurrent);
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext =
                new ConcreteClassContext(OrderedInvocationTest.class, engineContext, instance);

        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor classDescriptor = new ParamixelTestClassDescriptor(
                rootId.append("class", OrderedInvocationTest.class.getName()),
                OrderedInvocationTest.class,
                OrderedInvocationTest.class.getName());
        classDescriptor.setArgumentParallelism(4);
        final ParamixelTestArgumentDescriptor argDescriptor = new ParamixelTestArgumentDescriptor(
                classDescriptor.getUniqueId().append("argument", "0"), 0, "arg", "argument:0");
        classDescriptor.addChild(argDescriptor);

        final Method ordered = OrderedInvocationTest.class.getDeclaredMethod("ordered", ArgumentContext.class);
        final Method plain = OrderedInvocationTest.class.getDeclaredMethod("plain", ArgumentContext.class);
        argDescriptor.addChild(new ParamixelTestMethodDescriptor(
                argDescriptor.getUniqueId().append("method", "ordered"), ordered, "ordered"));
        argDescriptor.addChild(new ParamixelTestMethodDescriptor(
                argDescriptor.getUniqueId().append("method", "plain"), plain, "plain"));

        final RecordingListener listener = new RecordingListener();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(4)) {
            final ParamixelInvocationRunner runner =
                    new ParamixelInvocationRunner(runtime, listener, classContext, instance);

            final TestExecutionResult result = runner.runInvocations(classDescriptor, "arg", 0);
            assertThat(result.getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        }

        assertThat(maxConcurrent.get()).isEqualTo(1);
        assertThat(threads).hasSize(1);
    }

    @Test
    public void runsBeforeEachAndAfterEach_inExpectedInheritanceOrder_andReportsResults() throws Exception {
        final OrderingTest instance = new OrderingTest();
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(OrderingTest.class, engineContext, instance);

        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor classDescriptor = new ParamixelTestClassDescriptor(
                rootId.append("class", OrderingTest.class.getName()), OrderingTest.class, OrderingTest.class.getName());
        classDescriptor.setArgumentParallelism(1);
        final ParamixelTestArgumentDescriptor argDescriptor = new ParamixelTestArgumentDescriptor(
                classDescriptor.getUniqueId().append("argument", "0"), 0, "arg", "argument:0");
        classDescriptor.addChild(argDescriptor);

        final Method okMethod = OrderingTest.class.getDeclaredMethod("ok", ArgumentContext.class);
        final Method failMethod = OrderingTest.class.getDeclaredMethod("fails", ArgumentContext.class);
        argDescriptor.addChild(
                new ParamixelTestMethodDescriptor(argDescriptor.getUniqueId().append("method", "ok"), okMethod, "ok"));
        argDescriptor.addChild(new ParamixelTestMethodDescriptor(
                argDescriptor.getUniqueId().append("method", "fails"), failMethod, "fails"));

        final RecordingListener listener = new RecordingListener();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(2)) {
            final ParamixelInvocationRunner runner =
                    new ParamixelInvocationRunner(runtime, listener, classContext, instance);

            final TestExecutionResult result = runner.runInvocations(classDescriptor, "arg", 0);
            assertThat(result.getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);
        }

        assertThat(classContext.getFirstFailure()).isNotNull();
        assertThat(listener.started).containsExactly("ok", "fails");
        assertThat(listener.finished).containsExactly("ok", "fails");
        assertThat(listener.results.get("ok").getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        assertThat(listener.results.get("fails").getStatus()).isEqualTo(TestExecutionResult.Status.FAILED);

        assertThat(instance.calls)
                .containsSubsequence("baseBeforeEach", "subBeforeEach", "ok", "baseAfterEach", "subAfterEach")
                .containsSubsequence("baseBeforeEach", "subBeforeEach", "fails", "baseAfterEach", "subAfterEach");
    }

    public static final class ParallelInvocationTest {

        private final CountDownLatch ready;
        private final CountDownLatch start;
        private final Set<String> threads;
        private final AtomicInteger concurrent;
        private final AtomicInteger maxConcurrent;

        private ParallelInvocationTest(
                final CountDownLatch ready,
                final CountDownLatch start,
                final Set<String> threads,
                final AtomicInteger concurrent,
                final AtomicInteger maxConcurrent) {
            this.ready = ready;
            this.start = start;
            this.threads = threads;
            this.concurrent = concurrent;
            this.maxConcurrent = maxConcurrent;
        }

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
            threads.add(Thread.currentThread().getName());
            final int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            ready.countDown();
            start.await(15, TimeUnit.SECONDS);
            concurrent.decrementAndGet();
        }
    }

    public static final class OrderedInvocationTest {

        private final Set<String> threads;
        private final AtomicInteger concurrent;
        private final AtomicInteger maxConcurrent;

        private OrderedInvocationTest(
                final Set<String> threads, final AtomicInteger concurrent, final AtomicInteger maxConcurrent) {
            this.threads = threads;
            this.concurrent = concurrent;
            this.maxConcurrent = maxConcurrent;
        }

        @Paramixel.ArgumentsCollector
        public static void arguments(final ArgumentsCollector collector) {
            collector.setParallelism(4);
            collector.addArgument("arg");
        }

        @Paramixel.Test
        @Paramixel.Order(1)
        public void ordered(final ArgumentContext context) {
            doWork();
        }

        @Paramixel.Test
        public void plain(final ArgumentContext context) {
            doWork();
        }

        private void doWork() {
            threads.add(Thread.currentThread().getName());
            final int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            concurrent.decrementAndGet();
        }
    }

    public static class OrderingBase {

        final List<String> calls = new ArrayList<>();

        @Paramixel.BeforeEach
        public void baseBeforeEach(final ArgumentContext context) {
            calls.add("baseBeforeEach");
        }

        @Paramixel.AfterEach
        public void baseAfterEach(final ArgumentContext context) {
            calls.add("baseAfterEach");
        }
    }

    public static class OrderingTest extends OrderingBase {

        @Paramixel.ArgumentsCollector
        public static void arguments(final ArgumentsCollector collector) {
            collector.setParallelism(1);
            collector.addArgument("arg");
        }

        @Paramixel.BeforeEach
        public void subBeforeEach(final ArgumentContext context) {
            calls.add("subBeforeEach");
        }

        @Paramixel.AfterEach
        public void subAfterEach(final ArgumentContext context) {
            calls.add("subAfterEach");
        }

        @Paramixel.Test
        public void ok(final ArgumentContext context) {
            calls.add("ok");
        }

        @Paramixel.Test
        public void fails(final ArgumentContext context) {
            calls.add("fails");
            throw new RuntimeException("boom");
        }
    }

    private static final class RecordingListener implements EngineExecutionListener {

        final List<String> started = new ArrayList<>();
        final List<String> finished = new ArrayList<>();
        final Map<String, TestExecutionResult> results = new HashMap<>();

        @Override
        public void executionStarted(final TestDescriptor testDescriptor) {
            started.add(testDescriptor.getDisplayName());
        }

        @Override
        public void executionFinished(
                final TestDescriptor testDescriptor, final TestExecutionResult testExecutionResult) {
            finished.add(testDescriptor.getDisplayName());
            results.put(testDescriptor.getDisplayName(), testExecutionResult);
        }
    }
}
