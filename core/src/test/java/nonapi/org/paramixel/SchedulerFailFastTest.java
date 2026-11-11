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
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.exception.AbortException;

@DisplayName("Scheduler fail-fast")
class SchedulerFailFastTest {

    @Test
    @DisplayName("fail fast skips remaining root children after leaf failure (parallelism=1)")
    void failFastSkipsRemainingRootChildrenAfterLeafFailure() {
        var executed = new AtomicInteger();
        var root = Parallel.parallel("root", "p1x0")
                .child(Step.step("fail", ctx -> {
                    executed.incrementAndGet();
                    throw new RuntimeException("boom");
                }))
                .child(Step.step("skip-1", ctx -> executed.incrementAndGet()))
                .child(Step.step("skip-2", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(true, 1).run(root);

        assertThat(result.status().isFailed()).isTrue();
        assertThat(executed.get()).isEqualTo(1);
        var children = result.descriptor().orElseThrow().children();
        assertThat(children.get(0).status().isFailed()).isTrue();
        assertThat(children.get(1).status().isSkipped()).isTrue();
        assertThat(children.get(2).status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("fail fast skip cascades SKIPPED to descendants of the skipped action")
    void failFastSkipCascadesToDescendants() {
        var root = Parallel.parallel("root", "p1x1")
                .child(Step.step("fail", ctx -> {
                    throw new RuntimeException("boom");
                }))
                .child(Sequential.sequential("skipped-seq")
                        .child(Step.step("inner-1", ctx -> {}))
                        .child(Step.step("inner-2", ctx -> {}))
                        .build())
                .build();

        var result = runner(true, 1).run(root);

        assertThat(result.status().isFailed()).isTrue();
        var children = result.descriptor().orElseThrow().children();
        assertThat(children.get(0).status().isFailed()).isTrue();
        assertThat(children.get(1).status().isSkipped()).isTrue();
        var grandchildren = children.get(1).children();
        assertThat(grandchildren).hasSize(2);
        assertThat(grandchildren.get(0).status().isSkipped()).isTrue();
        assertThat(grandchildren.get(1).status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("fail fast does not affect nested children of already-running subtrees")
    void failFastDoesNotAffectNestedChildren() {
        var nestedExecuted = new AtomicInteger();
        var nestedStarted = new CountDownLatch(1);
        var root = Parallel.parallel("root", "p2x2")
                .child(Sequential.sequential("nested-seq")
                        .child(Step.step("nested-1", ctx -> {
                            nestedExecuted.incrementAndGet();
                            nestedStarted.countDown();
                        }))
                        .child(Step.step("nested-2", ctx -> nestedExecuted.incrementAndGet()))
                        .independent()
                        .build())
                .child(Step.step("fail", ctx -> {
                    if (!nestedStarted.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("nested subtree did not start before fail step");
                    }
                    throw new RuntimeException("boom");
                }))
                .build();

        var result = runner(true, 2).run(root);

        assertThat(result.status().isFailed()).isTrue();
        assertThat(nestedExecuted.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("fail fast disabled all children run despite failure")
    void failFastDisabledAllChildrenRun() {
        var executed = new AtomicInteger();
        var root = Parallel.parallel("root", "p1x0")
                .child(Step.step("fail", ctx -> {
                    executed.incrementAndGet();
                    throw new RuntimeException("boom");
                }))
                .child(Step.step("runs-1", ctx -> executed.incrementAndGet()))
                .child(Step.step("runs-2", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(false, 1).run(root);

        assertThat(result.status().isFailed()).isTrue();
        assertThat(executed.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("fail fast with no failures all children run")
    void failFastWithNoFailuresAllChildrenRun() {
        var executed = new AtomicInteger();
        var root = Parallel.parallel("root", "p1x1")
                .child(Step.step("pass-1", ctx -> executed.incrementAndGet()))
                .child(Step.step("pass-2", ctx -> executed.incrementAndGet()))
                .child(Step.step("pass-3", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(true, 1).run(root);

        assertThat(result.status().isPassed()).isTrue();
        assertThat(executed.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("fail fast with aborted action triggers skip")
    void failFastWithAbortedActionTriggersSkip() {
        var executed = new AtomicInteger();
        var root = Parallel.parallel("root", "p1x2")
                .child(Step.step("abort", ctx -> {
                    executed.incrementAndGet();
                    throw AbortException.abort("aborted");
                }))
                .child(Step.step("skip-1", ctx -> executed.incrementAndGet()))
                .child(Step.step("skip-2", ctx -> executed.incrementAndGet()))
                .build();

        var result = runner(true, 1).run(root);

        assertThat(result.status().isFailed()).isTrue();
        assertThat(executed.get()).isEqualTo(1);
        var children = result.descriptor().orElseThrow().children();
        assertThat(children.get(0).status().isAborted()).isTrue();
        assertThat(children.get(1).status().isSkipped()).isTrue();
        assertThat(children.get(2).status().isSkipped()).isTrue();
    }

    private static Runner runner(final boolean failFast, final int parallelism) {
        var configuration = TestPools.configuration(Map.of(
                Configuration.RUNNER_PARALLELISM,
                String.valueOf(parallelism),
                "paramixel.scheduler.queue.capacity",
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
