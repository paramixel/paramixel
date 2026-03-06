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

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

public class AbstractEngineExecutionListenerTest {

    @Test
    public void getDisplayName_buildsFromHierarchyUpToDepth() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");
        final var m = AbstractEngineExecutionListenerTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        clazz.addChild(arg);
        arg.addChild(method);

        final TestableListener listener = new TestableListener();
        assertThat(listener.displayName(1, method)).isEqualTo("M");
        assertThat(listener.displayName(2, method)).isEqualTo("A0 | M");
        assertThat(listener.displayName(3, method)).isEqualTo("C | A0 | M");
    }

    @Test
    public void getStatusMessage_includesThreadAndDisplayName_andVariesByStatus() {
        final TestableListener listener = new TestableListener();
        assertThat(listener.statusMessage(TestExecutionResult.successful(), "t", "d"))
                .contains("t")
                .contains("d")
                .contains("PASS");

        assertThat(listener.statusMessage(TestExecutionResult.failed(new RuntimeException("boom")), "t", "d"))
                .contains("t")
                .contains("d")
                .contains("FAIL");

        assertThat(listener.statusMessage(TestExecutionResult.aborted(new RuntimeException("abort")), "t", "d"))
                .contains("t")
                .contains("d")
                .contains("ABORTED");
    }

    @Test
    public void getStatusMessage_includesDuration() {
        final TestableListener listener = new TestableListener();
        assertThat(listener.statusMessage(TestExecutionResult.successful(), "t", "d", 1500L))
                .contains("t")
                .contains("d")
                .contains("PASS")
                .contains("1.500 s");

        assertThat(listener.statusMessage(TestExecutionResult.successful(), "t", "d", 500L))
                .contains("t")
                .contains("d")
                .contains("PASS")
                .contains("500 ms");
    }

    private static void dummy() {
        // INTENTIONALLY EMPTY
    }

    @Test
    public void recordStart_capturesStartTime() {
        final TestableListener listener = new TestableListener();
        final AbstractEngineExecutionListener.ExecutionSummary summary = listener.getExecutionSummary();
        summary.reset();

        summary.recordStart("test-descriptor");

        assertThat(summary.getClassDuration("test-descriptor")).isEqualTo(0L);
    }

    @Test
    public void recordEnd_calculatesDuration() throws InterruptedException {
        final TestableListener listener = new TestableListener();
        final AbstractEngineExecutionListener.ExecutionSummary summary = listener.getExecutionSummary();
        summary.reset();

        summary.recordStart("test-descriptor");
        Thread.sleep(10L);
        summary.recordEnd("test-descriptor", summary.getClassDurations());

        assertThat(summary.getClassDuration("test-descriptor")).isGreaterThanOrEqualTo(10L);
    }

    @Test
    public void getClassDuration_returnsZeroWhenNoData() {
        final TestableListener listener = new TestableListener();
        final AbstractEngineExecutionListener.ExecutionSummary summary = listener.getExecutionSummary();
        summary.reset();

        assertThat(summary.getClassDuration("non-existent")).isEqualTo(0L);
    }

    @Test
    public void classStats_tracksTotalDurationMillis() {
        final AbstractEngineExecutionListener.ExecutionSummary.ClassStats stats =
                new AbstractEngineExecutionListener.ExecutionSummary.ClassStats("TestClass");

        stats.totalDurationMillis.addAndGet(100L);
        stats.totalDurationMillis.addAndGet(200L);

        assertThat(stats.getTotalDurationMillis()).isEqualTo(300L);
    }

    @Test
    public void reset_clearsTimingData() {
        final TestableListener listener = new TestableListener();
        final AbstractEngineExecutionListener.ExecutionSummary summary = listener.getExecutionSummary();

        summary.recordStart("test-descriptor");
        summary.recordEnd("test-descriptor", summary.getClassDurations());
        summary.reset();

        assertThat(summary.getClassDuration("test-descriptor")).isEqualTo(0L);
    }

    private static final class TestableListener extends AbstractEngineExecutionListener {

        String displayName(final int depth, final TestDescriptor descriptor) {
            return getDisplayName(depth, descriptor);
        }

        String statusMessage(final TestExecutionResult result, final String thread, final String displayName) {
            return getStatusMessage(result, thread, displayName, 0L);
        }

        String statusMessage(
                final TestExecutionResult result,
                final String thread,
                final String displayName,
                final long durationMillis) {
            return getStatusMessage(result, thread, displayName, durationMillis);
        }
    }
}
