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

package org.paramixel.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Status;
import org.paramixel.core.action.Noop;

@DisplayName("DefaultResult")
class DefaultResultTest {

    @Test
    @DisplayName("single arg constructor creates staged result with zero duration")
    void singleArgConstructorCreatesStagedResult() {
        var result = new DefaultResult(Noop.of("test"));

        assertThat(result.getStatus().isStaged()).isTrue();
        assertThat(result.getRunDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("three arg constructor sets status and duration atomically")
    void threeArgConstructorSetsStatusAndDuration() {
        var status = DefaultStatus.PASS;
        var timing = Duration.ofMillis(42);

        var result = new DefaultResult(Noop.of("test"), status, timing);

        assertThat(result.getStatus()).isSameAs(status);
        assertThat(result.getRunDuration()).isEqualTo(timing);
    }

    @Test
    @DisplayName("complete atomically sets both status and duration")
    void completeAtomicallySetsBothStatusAndDuration() {
        var result = new DefaultResult(Noop.of("test"));
        var timing = Duration.ofMillis(99);

        result.complete(DefaultStatus.PASS, timing);

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getRunDuration()).isEqualTo(timing);
    }

    @Test
    @DisplayName("complete rejects null status")
    void completeRejectsNullStatus() {
        var result = new DefaultResult(Noop.of("test"));

        assertThatThrownBy(() -> result.complete(null, Duration.ZERO))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    @DisplayName("complete rejects null run duration")
    void completeRejectsNullRunDuration() {
        var result = new DefaultResult(Noop.of("test"));

        assertThatThrownBy(() -> result.complete(DefaultStatus.PASS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("runDuration must not be null");
    }

    @Test
    @DisplayName("setStatus preserves current run duration")
    void setStatusPreservesCurrentRunDuration() {
        var result = new DefaultResult(Noop.of("test"));
        result.complete(DefaultStatus.PASS, Duration.ofMillis(50));

        result.setStatus(DefaultStatus.SKIP);

        assertThat(result.getStatus().isSkip()).isTrue();
        assertThat(result.getRunDuration()).isEqualTo(Duration.ofMillis(50));
    }

    @Test
    @DisplayName("setRunDuration preserves current status")
    void setRunDurationPreservesCurrentStatus() {
        var result = new DefaultResult(Noop.of("test"));
        result.complete(DefaultStatus.PASS, Duration.ofMillis(50));

        result.setRunDuration(Duration.ofMillis(200));

        assertThat(result.getStatus().isPass()).isTrue();
        assertThat(result.getRunDuration()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("setStatus rejects null")
    void setStatusRejectsNull() {
        var result = new DefaultResult(Noop.of("test"));

        assertThatThrownBy(() -> result.setStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    @DisplayName("setRunDuration rejects null")
    void setRunDurationRejectsNull() {
        var result = new DefaultResult(Noop.of("test"));

        assertThatThrownBy(() -> result.setRunDuration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("runDuration must not be null");
    }

    @Test
    @DisplayName("three arg constructor rejects null action")
    void threeArgConstructorRejectsNullAction() {
        assertThatThrownBy(() -> new DefaultResult(null, DefaultStatus.PASS, Duration.ZERO))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("action must not be null");
    }

    @Test
    @DisplayName("three arg constructor rejects null status")
    void threeArgConstructorRejectsNullStatus() {
        assertThatThrownBy(() -> new DefaultResult(Noop.of("test"), null, Duration.ZERO))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }

    @Test
    @DisplayName("three arg constructor rejects null run duration")
    void threeArgConstructorRejectsNullRunDuration() {
        assertThatThrownBy(() -> new DefaultResult(Noop.of("test"), DefaultStatus.PASS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("runDuration must not be null");
    }

    @Test
    @DisplayName("toString returns expected format")
    void toStringReturnsExpectedFormat() {
        var result = new DefaultResult(Noop.of("test"));
        result.complete(DefaultStatus.PASS, Duration.ofMillis(123));

        assertThat(result.toString()).isEqualTo("PASS | 123 ms");
    }

    @Test
    @DisplayName("concurrent readers never observe completed status with zero duration")
    void concurrentReadersNeverObserveCompletedStatusWithZeroDuration() throws InterruptedException {
        int iterations = 100;
        int readersPerIteration = 4;
        var failures = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(readersPerIteration + 1);

        try {
            for (int i = 0; i < iterations; i++) {
                var result = new DefaultResult(Noop.of("test-" + i));
                var ready = new CountDownLatch(readersPerIteration);
                var go = new CountDownLatch(1);
                var finished = new CountDownLatch(readersPerIteration);

                for (int r = 0; r < readersPerIteration; r++) {
                    executor.submit(() -> {
                        ready.countDown();
                        try {
                            go.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        Status status = result.getStatus();
                        Duration duration = result.getRunDuration();
                        if (status.isPass() && duration.equals(Duration.ZERO)) {
                            failures.incrementAndGet();
                        }
                        finished.countDown();
                    });
                }

                ready.await();
                result.complete(DefaultStatus.PASS, Duration.ofMillis(1));
                go.countDown();
                finished.await();
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(failures.get()).isZero();
    }

    @Test
    @DisplayName("concurrent complete calls produce consistent state")
    void concurrentCompleteCallsProduceConsistentState() throws InterruptedException {
        int iterations = 200;
        var failures = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < iterations; i++) {
                var result = new DefaultResult(Noop.of("test-" + i));
                var ready = new CountDownLatch(2);
                var done = new CountDownLatch(2);

                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    result.complete(DefaultStatus.PASS, Duration.ofMillis(10));
                    done.countDown();
                });

                executor.submit(() -> {
                    ready.countDown();
                    try {
                        ready.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    result.complete(DefaultStatus.SKIP, Duration.ZERO);
                    done.countDown();
                });

                done.await();

                Status status = result.getStatus();
                Duration duration = result.getRunDuration();
                boolean passWithCorrectDuration = status.isPass() && duration.equals(Duration.ofMillis(10));
                boolean skipWithZeroDuration = status.isSkip() && duration.equals(Duration.ZERO);
                if (!passWithCorrectDuration && !skipWithZeroDuration) {
                    failures.incrementAndGet();
                }
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(failures.get())
                .as("status and duration must always be from the same complete() call")
                .isZero();
    }
}
