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

package org.paramixel.engine.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

class ParamixelEngineExecutionListenerTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void updatesExecutionSummaryCountersForClassArgumentAndMethod() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");
        final var m = ParamixelEngineExecutionListenerTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        clazz.addChild(arg);
        arg.addChild(method);

        final ExposingListener listener = new ExposingListener();
        listener.reset();

        listener.executionStarted(clazz);
        listener.executionStarted(arg);
        listener.executionStarted(method);
        listener.executionFinished(method, TestExecutionResult.successful());
        listener.executionFinished(arg, TestExecutionResult.successful());
        listener.executionFinished(clazz, TestExecutionResult.failed(new RuntimeException("boom")));

        final var summary = listener.summary();
        assertThat(summary.getTestClassFailed()).isEqualTo(1);
        assertThat(summary.getTestArgumentPassed()).isEqualTo(1);
        assertThat(summary.getTestMethodPassed()).isEqualTo(1);
        assertThat(summary.getClassArgumentCount("C")).isEqualTo(1);
        assertThat(summary.getClassMethodCount("C")).isEqualTo(1);
    }

    @Test
    public void executionSkipped_andAbortedResults_incrementSkippedCounters() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");
        final var m = ParamixelEngineExecutionListenerTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        clazz.addChild(arg);
        arg.addChild(method);

        final ExposingListener listener = new ExposingListener();
        listener.reset();

        listener.executionSkipped(method, "reason");
        listener.executionFinished(method, TestExecutionResult.aborted(new RuntimeException("abort")));
        listener.executionSkipped(arg, "reason");
        listener.executionSkipped(clazz, "reason");

        final var summary = listener.summary();
        assertThat(summary.getTestMethodSkipped()).isEqualTo(2);
        assertThat(summary.getTestArgumentSkipped()).isEqualTo(1);
        assertThat(summary.getTestClassSkipped()).isEqualTo(1);
    }

    @Test
    public void executionFinished_successfulClassAndFailedArgumentAndMethod_updateCounters() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");
        final var m = ParamixelEngineExecutionListenerTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        clazz.addChild(arg);
        arg.addChild(method);

        final ExposingListener listener = new ExposingListener();
        listener.reset();

        listener.executionFinished(method, TestExecutionResult.failed(new RuntimeException("method fail")));
        listener.executionFinished(arg, TestExecutionResult.failed(new RuntimeException("arg fail")));
        listener.executionFinished(clazz, TestExecutionResult.successful());

        final var summary = listener.summary();
        assertThat(summary.getTestClassPassed()).isEqualTo(1);
        assertThat(summary.getTestArgumentFailed()).isEqualTo(1);
        assertThat(summary.getTestMethodFailed()).isEqualTo(1);
        assertThat(summary.getClassStatsMap().get("C")).isNotNull();
        assertThat(summary.getClassStatsMap().get("C").passed.get()).isEqualTo(1);
    }

    @Test
    public void unknownDescriptorType_fallsBackToConsoleOutput() {
        final ExposingListener listener = new ExposingListener();
        listener.reset();

        final DummyDescriptor dummy =
                new DummyDescriptor(UniqueId.forEngine("paramixel").append("dummy", "1"));
        listener.executionStarted(dummy);
        listener.executionSkipped(dummy, "because");
        listener.executionFinished(dummy, TestExecutionResult.successful());

        assertThat(out.toString()).contains("START").contains("SKIPPED").contains("FINISH");
    }

    @Test
    public void argumentOrMethodWithoutClassParent_doesNotIncrementClassCounters() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(rootId.append("argument", "0"), 0, null, "A0");
        final var m = ParamixelEngineExecutionListenerTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        arg.addChild(method);

        final ExposingListener listener = new ExposingListener();
        listener.reset();

        listener.executionStarted(arg);
        listener.executionStarted(method);

        assertThat(listener.summary().getClassArgumentCount("C")).isZero();
        assertThat(listener.summary().getClassMethodCount("C")).isZero();
    }

    @Test
    public void executionSummary_tracksCurrentClassName_andClassStatsTotal() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");

        final ExposingListener listener = new ExposingListener();
        listener.reset();

        listener.executionStarted(clazz);
        assertThat(listener.summary().getCurrentClassName()).isEqualTo("C");

        listener.executionFinished(clazz, TestExecutionResult.successful());
        assertThat(listener.summary().getClassStatsMap().get("C").getTotal()).isEqualTo(1);

        listener.reset();
        listener.executionFinished(clazz, TestExecutionResult.failed(new RuntimeException("boom")));
        assertThat(listener.summary().getClassStatsMap().get("C").getTotal()).isEqualTo(1);
    }

    private static void dummy() {}

    private static final class ExposingListener extends ParamixelEngineExecutionListener {
        void reset() {
            resetExecutionSummary();
        }

        ExecutionSummary summary() {
            return getExecutionSummary();
        }
    }

    private static final class DummyDescriptor extends AbstractTestDescriptor {
        private DummyDescriptor(final UniqueId uniqueId) {
            super(uniqueId, "dummy");
        }

        @Override
        public Type getType() {
            return Type.TEST;
        }
    }
}
