/*
 * Copyright (c) 2026-present Douglas Hoard
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

package nonapi.org.paramixel.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import nonapi.org.paramixel.ConcreteConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;

@DisplayName("SummaryListener getWriter")
class SummaryListenerTest {

    @Test
    @DisplayName("concurrent getWriter does not close System.out")
    void concurrentGetWriterDoesNotCloseSystemOut() throws Exception {
        var threadCount = 8;
        var barrier = new CyclicBarrier(threadCount);
        var errors = new AtomicInteger();

        var listener = new SummaryListener();
        listener.initialize(new ConcreteConfiguration(Map.of(Configuration.ANSI, "false")));

        var threads = new Thread[threadCount];
        for (var i = 0; i < threadCount; i++) {
            threads[i] = new Thread(
                    () -> {
                        try {
                            barrier.await();
                            listener.onRunStarted();
                        } catch (Throwable t) {
                            errors.incrementAndGet();
                        }
                    },
                    "getWriter-test-" + i);
        }

        var stdoutCapture = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(stdoutCapture, true, StandardCharsets.UTF_8));
            for (var t : threads) {
                t.start();
            }
            for (var t : threads) {
                t.join();
            }
        } finally {
            System.setOut(originalOut);
        }

        assertThat(errors.get()).isZero();

        // Verify System.out is still functional after concurrent getWriter calls
        var verifyCapture = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(verifyCapture, true, StandardCharsets.UTF_8));
            System.out.println("still works");
        } finally {
            System.setOut(originalOut);
        }
        assertThat(verifyCapture.toString(StandardCharsets.UTF_8)).contains("still works");
    }

    @Test
    @DisplayName("onRunStarted produces output to System.out")
    void onRunStartedProducesOutput() {
        var listener = new SummaryListener();
        listener.initialize(new ConcreteConfiguration(Map.of(Configuration.ANSI, "false")));

        var capture = new ByteArrayOutputStream();
        var originalOut = System.out;
        try {
            System.setOut(new PrintStream(capture, true, StandardCharsets.UTF_8));
            listener.onRunStarted();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(capture.toString(StandardCharsets.UTF_8)).contains("starting...");
    }
}
