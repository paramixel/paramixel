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

package org.paramixel.api.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Repeat execution")
class RepeatExecutionTest {

    @Nested
    @DisplayName("dependent mode")
    class Dependent {

        @Test
        @DisplayName("second rep aborts skips remaining")
        void secondRepAbortsSkipsRemaining() {
            var counter = new AtomicInteger();

            var action = Repeat.of("repeat")
                    .count(3)
                    .child("step", ctx -> {
                        int count = counter.incrementAndGet();
                        if (count == 2) {
                            AbortedException.abort("aborted");
                        }
                    })
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isAborted()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(counter.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("second rep skips skips remaining")
        void secondRepSkipsSkipsRemaining() {
            var counter = new AtomicInteger();

            var action = Repeat.of("repeat")
                    .count(3)
                    .child("step", ctx -> {
                        int count = counter.incrementAndGet();
                        if (count == 2) {
                            SkipException.skip("skipped");
                        }
                    })
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(counter.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("second rep runtime exception skips remaining")
        void secondRepRuntimeExceptionSkipsRemaining() {
            var counter = new AtomicInteger();

            var action = Repeat.of("repeat")
                    .count(3)
                    .child("step", ctx -> {
                        int count = counter.incrementAndGet();
                        if (count == 2) {
                            throw new RuntimeException("runtime error");
                        }
                    })
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isFailed()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(counter.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("independent mode")
    class Independent {

        @Test
        @DisplayName("second rep aborts all still run")
        void secondRepAbortsAllStillRun() {
            var counter = new AtomicInteger();

            var action = Repeat.of("repeat")
                    .count(3)
                    .independent()
                    .child("step", ctx -> {
                        int count = counter.incrementAndGet();
                        if (count == 2) {
                            AbortedException.abort("aborted");
                        }
                    })
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.ABORTED);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isAborted()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(counter.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("second rep skips all still run")
        void secondRepSkipsAllStillRun() {
            var counter = new AtomicInteger();

            var action = Repeat.of("repeat")
                    .count(3)
                    .independent()
                    .child("step", ctx -> {
                        int count = counter.incrementAndGet();
                        if (count == 2) {
                            SkipException.skip("skipped");
                        }
                    })
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status()).isSameAs(Status.SKIPPED);
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isSkipped()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(counter.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("second rep runtime exception all still run")
        void secondRepRuntimeExceptionAllStillRun() {
            var counter = new AtomicInteger();

            var action = Repeat.of("repeat")
                    .count(3)
                    .independent()
                    .child("step", ctx -> {
                        int count = counter.incrementAndGet();
                        if (count == 2) {
                            throw new RuntimeException("runtime error");
                        }
                    })
                    .resolve();

            var root = Runner.builder().build().run(action).descriptor().orElseThrow();
            var children = root.children();

            assertThat(root.metadata().status().isFailed()).isTrue();
            assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
            assertThat(children.get(1).metadata().status().isFailed()).isTrue();
            assertThat(children.get(2).metadata().status()).isSameAs(Status.PASSED);
            assertThat(counter.get()).isEqualTo(3);
        }
    }
}
