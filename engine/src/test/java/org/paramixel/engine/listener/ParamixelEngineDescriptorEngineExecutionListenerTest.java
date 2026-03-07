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

package org.paramixel.engine.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertThat(output).contains("Paramixel Test Summary");
        // Verify title row format with duration separator
        assertThat(output).contains(" | ");
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

        // Test milliseconds duration
        engineListener.executionStarted(engine);
        setStartTimeMillis(engineListener, System.currentTimeMillis() - 250);
        routingListener.executionStarted(clazz);
        routingListener.executionStarted(arg);
        routingListener.executionStarted(method);
        routingListener.executionFinished(method, TestExecutionResult.successful());
        routingListener.executionFinished(arg, TestExecutionResult.successful());
        routingListener.executionFinished(clazz, TestExecutionResult.successful());
        engineListener.executionFinished(engine, TestExecutionResult.successful());

        // Test seconds duration
        engineListener.executionStarted(engine);
        setStartTimeMillis(engineListener, System.currentTimeMillis() - 2500);
        routingListener.executionStarted(clazz);
        routingListener.executionStarted(arg);
        routingListener.executionStarted(method);
        routingListener.executionFinished(method, TestExecutionResult.successful());
        routingListener.executionFinished(arg, TestExecutionResult.successful());
        routingListener.executionFinished(clazz, TestExecutionResult.successful());
        engineListener.executionFinished(engine, TestExecutionResult.successful());

        // Test minutes duration
        engineListener.executionStarted(engine);
        setStartTimeMillis(engineListener, System.currentTimeMillis() - 65_000);
        routingListener.executionStarted(clazz);
        routingListener.executionStarted(arg);
        routingListener.executionStarted(method);
        routingListener.executionFinished(method, TestExecutionResult.successful());
        routingListener.executionFinished(arg, TestExecutionResult.successful());
        routingListener.executionFinished(clazz, TestExecutionResult.successful());
        engineListener.executionFinished(engine, TestExecutionResult.successful());

        final String output = out.toString();
        // Verify title row format - duration is now in "Paramixel Test Summary | {duration}"
        assertThat(output).contains("Paramixel Test Summary");
        assertThat(output).contains(" | ");
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

    @Test
    public void tableBordersAreAligned_andClassNamesAreNotTruncated_andColumnsExpandForData() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelEngineDescriptor engine = new ParamixelEngineDescriptor(rootId, "paramixel");

        final String longClassName = "test." + "VeryLongClassName_".repeat(12) + "Tail";
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, longClassName);
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

        AbstractEngineExecutionListener.ExecutionSummary summary = AbstractEngineExecutionListener.EXECUTION_SUMMARY;
        AbstractEngineExecutionListener.ExecutionSummary.ClassStats stats =
                summary.getClassStatsMap().get(longClassName);
        stats.passed.set(123456);
        stats.failed.set(0);
        stats.totalDurationMillis.set(9876543210L);
        setClassArgumentCount(summary, longClassName, 12345);
        setClassMethodCount(summary, longClassName, 678901);

        engineListener.executionFinished(engine, TestExecutionResult.successful());

        final String output = out.toString();
        String normalizedOutput = stripAnsi(output);
        assertThat(normalizedOutput).contains(longClassName);

        List<Integer> tableLineLengths = getTableLineLengths(normalizedOutput);
        assertThat(tableLineLengths).as("Should have found table lines").isNotEmpty();
        int expectedLength = tableLineLengths.get(0);
        for (int i = 1; i < tableLineLengths.size(); i++) {
            assertThat(tableLineLengths.get(i))
                    .as("Table line " + i + " should have same length (" + expectedLength + ") as first line")
                    .isEqualTo(expectedLength);
        }
    }

    @Test
    public void tableClassNamesAreAbbreviatedWhenSummaryClassNameMaxLengthIsConfigured() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelEngineDescriptor engine = new ParamixelEngineDescriptor(rootId, "paramixel");

        final String fullClassName = "com.example.deep.pkg.ClassName";
        final String expectedRendered = "c.e.d.pkg.ClassName";

        final ParamixelEngineDescriptorEngineExecutionListener engineListener =
                new ParamixelEngineDescriptorEngineExecutionListener(printer, 19);

        engineListener.executionStarted(engine);

        AbstractEngineExecutionListener.ExecutionSummary summary = AbstractEngineExecutionListener.EXECUTION_SUMMARY;
        AbstractEngineExecutionListener.ExecutionSummary.ClassStats stats =
                new AbstractEngineExecutionListener.ExecutionSummary.ClassStats(fullClassName);
        summary.getClassStatsMap().put(fullClassName, stats);
        stats.passed.set(1);
        stats.failed.set(0);
        stats.totalDurationMillis.set(10L);
        setClassArgumentCount(summary, fullClassName, 1);
        setClassMethodCount(summary, fullClassName, 1);

        engineListener.executionFinished(engine, TestExecutionResult.successful());

        final String normalizedOutput = stripAnsi(out.toString());
        assertThat(normalizedOutput).contains(expectedRendered);
        assertThat(normalizedOutput).doesNotContain(fullClassName);
    }

    private static List<Integer> getTableLineLengths(final String output) {
        String[] lines = output.split("\n");
        List<Integer> lengths = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("[INFO] +") || line.startsWith("[INFO] |")) {
                lengths.add(line.substring(7).length());
            }
        }
        return lengths;
    }

    private static String stripAnsi(final String input) {
        return input.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private static void setClassArgumentCount(
            final AbstractEngineExecutionListener.ExecutionSummary summary, final String className, final int count)
            throws Exception {
        final Field field =
                AbstractEngineExecutionListener.ExecutionSummary.class.getDeclaredField("classArgumentCounts");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, AtomicInteger> map = (Map<String, AtomicInteger>) field.get(summary);
        map.put(className, new AtomicInteger(count));
    }

    private static void setClassMethodCount(
            final AbstractEngineExecutionListener.ExecutionSummary summary, final String className, final int count)
            throws Exception {
        final Field field =
                AbstractEngineExecutionListener.ExecutionSummary.class.getDeclaredField("classMethodCounts");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, AtomicInteger> map = (Map<String, AtomicInteger>) field.get(summary);
        map.put(className, new AtomicInteger(count));
    }

    private static void dummy() {
        // INTENTIONALLY EMPTY
    }
}
