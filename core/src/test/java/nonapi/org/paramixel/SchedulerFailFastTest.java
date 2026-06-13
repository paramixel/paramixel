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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.action.Sequence;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortedException;

@DisplayName("Scheduler fail-fast")
@SuppressWarnings("removal")
class SchedulerFailFastTest {

    @Test
    @DisplayName("fail fast skips remaining root children after leaf failure (parallelism=1)")
    void failFastSkipsRemainingRootChildrenAfterLeafFailure() {
        var executed = new AtomicInteger();
        var root = Parallel.builder("root")
                .parallelism(1)
                .child(Step.of("fail", ctx -> {
                    executed.incrementAndGet();
                    throw new RuntimeException("boom");
                }))
                .child(Step.of("skip-1", ctx -> executed.incrementAndGet()))
                .child(Step.of("skip-2", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(true, 1).run(root);

        assertThat(result.isFailed()).isTrue();
        assertThat(executed.get()).isEqualTo(1);
        var children = result.descriptor().orElseThrow().children();
        assertThat(children.get(0).isFailed()).isTrue();
        assertThat(children.get(1).isSkipped()).isTrue();
        assertThat(children.get(2).isSkipped()).isTrue();
    }

    @Test
    @DisplayName("fail fast does not affect nested children of already-running subtrees")
    void failFastDoesNotAffectNestedChildren() {
        var nestedExecuted = new AtomicInteger();
        var nestedStarted = new CountDownLatch(1);
        var root = Parallel.builder("root")
                .parallelism(2)
                .child(Sequence.builder("nested-seq")
                        .child(Step.of("nested-1", ctx -> {
                            nestedExecuted.incrementAndGet();
                            nestedStarted.countDown();
                        }))
                        .child(Step.of("nested-2", ctx -> nestedExecuted.incrementAndGet()))
                        .independent()
                        .build())
                .child(Step.of("fail", ctx -> {
                    if (!nestedStarted.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("nested subtree did not start before fail step");
                    }
                    throw new RuntimeException("boom");
                }))
                .build();

        var result = runner(true, 2).run(root);

        assertThat(result.isFailed()).isTrue();
        assertThat(nestedExecuted.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("fail fast disabled all children run despite failure")
    void failFastDisabledAllChildrenRun() {
        var executed = new AtomicInteger();
        var root = Parallel.builder("root")
                .parallelism(1)
                .child(Step.of("fail", ctx -> {
                    executed.incrementAndGet();
                    throw new RuntimeException("boom");
                }))
                .child(Step.of("runs-1", ctx -> executed.incrementAndGet()))
                .child(Step.of("runs-2", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(false, 1).run(root);

        assertThat(result.isFailed()).isTrue();
        assertThat(executed.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("fail fast with no failures all children run")
    void failFastWithNoFailuresAllChildrenRun() {
        var executed = new AtomicInteger();
        var root = Parallel.builder("root")
                .parallelism(1)
                .child(Step.of("pass-1", ctx -> executed.incrementAndGet()))
                .child(Step.of("pass-2", ctx -> executed.incrementAndGet()))
                .child(Step.of("pass-3", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(true, 1).run(root);

        assertThat(result.isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("fail fast with aborted action triggers skip")
    void failFastWithAbortedActionTriggersSkip() {
        var executed = new AtomicInteger();
        var root = Parallel.builder("root")
                .parallelism(1)
                .child(Step.of("abort", ctx -> {
                    executed.incrementAndGet();
                    AbortedException.abort("aborted");
                }))
                .child(Step.of("skip-1", ctx -> executed.incrementAndGet()))
                .child(Step.of("skip-2", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(true, 1).run(root);

        assertThat(result.isFailed()).isTrue();
        assertThat(executed.get()).isEqualTo(1);
        var children = result.descriptor().orElseThrow().children();
        assertThat(children.get(0).isAborted()).isTrue();
        assertThat(children.get(1).isSkipped()).isTrue();
        assertThat(children.get(2).isSkipped()).isTrue();
    }

    private static Runner runner(final boolean failFast, final int parallelism) {
        var configuration = Configuration.of(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                Configuration.SCHEDULER_QUEUE_CAPACITY,
                "32",
                Configuration.ANSI,
                "false",
                Configuration.FAIL_FAST,
                String.valueOf(failFast)));
        return Runner.builder()
                .configuration(configuration)
                .listener(new Listener() {})
                .build();
    }
}
