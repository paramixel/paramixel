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
import java.lang.reflect.Field;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.engine.descriptor.ParamixelEngineDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

public class ParamixelEngineDescriptorEngineExecutionListenerTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream out;
    private PrintStream printStream;
    private Consumer<String> printer;

    @BeforeEach
    public void setUp() {
        originalOut = System.out;
        out = new ByteArrayOutputStream();
        printStream = new PrintStream(out);
        printer = line -> printStream.println(line);
        System.setOut(printStream);
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void printsReportWithoutThrowing() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelEngineDescriptor engine = new ParamixelEngineDescriptor(rootId, "paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");
        final var m = ParamixelEngineDescriptorEngineExecutionListenerTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        clazz.addChild(arg);
        arg.addChild(method);
        engine.addChild(clazz);

        final ParamixelEngineDescriptorEngineExecutionListener engineListener =
                new ParamixelEngineDescriptorEngineExecutionListener(printer);
        final ParamixelEngineExecutionListener routingListener = new ParamixelEngineExecutionListener(printer);

        engineListener.executionStarted(engine);
        routingListener.executionStarted(clazz);
        routingListener.executionStarted(arg);
        routingListener.executionStarted(method);
        routingListener.executionFinished(method, TestExecutionResult.successful());
        routingListener.executionFinished(arg, TestExecutionResult.successful());
        routingListener.executionFinished(clazz, TestExecutionResult.successful());
        engineListener.executionFinished(engine, TestExecutionResult.successful());

        final String output = out.toString();
        assertThat(output).contains("Paramixel Test Execution Report");
        assertThat(output).contains("Classes tested");
    }

    @Test
    public void printsNoClassesExecutedWhenNoClassEventsWereRecorded() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelEngineDescriptor engine = new ParamixelEngineDescriptor(rootId, "paramixel");

        final ParamixelEngineDescriptorEngineExecutionListener engineListener =
                new ParamixelEngineDescriptorEngineExecutionListener(printer);

        engineListener.executionStarted(engine);
        engineListener.executionFinished(engine, TestExecutionResult.successful());

        assertThat(out.toString()).contains("No test classes executed.");
    }

    @Test
    public void formatDuration_branchesAreExercised() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelEngineDescriptor engine = new ParamixelEngineDescriptor(rootId, "paramixel");
        final ParamixelEngineDescriptorEngineExecutionListener engineListener =
                new ParamixelEngineDescriptorEngineExecutionListener(printer);

        engineListener.executionStarted(engine);
        setStartTimeMillis(engineListener, System.currentTimeMillis() - 250);
        engineListener.executionFinished(engine, TestExecutionResult.successful());

        engineListener.executionStarted(engine);
        setStartTimeMillis(engineListener, System.currentTimeMillis() - 2500);
        engineListener.executionFinished(engine, TestExecutionResult.successful());

        engineListener.executionStarted(engine);
        setStartTimeMillis(engineListener, System.currentTimeMillis() - 65_000);
        engineListener.executionFinished(engine, TestExecutionResult.successful());

        final String output = out.toString();
        assertThat(output).contains("Duration:");
        assertThat(output).contains("ms");
        assertThat(output).contains("s");
        assertThat(output).contains("m");
    }

    private static void setStartTimeMillis(
            final ParamixelEngineDescriptorEngineExecutionListener listener, final long startTimeMillis)
            throws Exception {
        final Field field = ParamixelEngineDescriptorEngineExecutionListener.class.getDeclaredField("startTimeMillis");
        field.setAccessible(true);
        field.setLong(listener, startTimeMillis);
    }

    private static void dummy() {}
}
