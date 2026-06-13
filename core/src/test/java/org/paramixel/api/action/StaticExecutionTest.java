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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.exception.AbortedException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Static execution")
@SuppressWarnings("removal")
class StaticExecutionTest {

    @Test
    @DisplayName("all pass with before, dependent body, and after")
    void allPass() {
        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> {}))
                        .child(Step.of("child-2", context -> {}))
                        .build())
                .after(Step.of("after", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isPassed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
    }

    @Test
    @DisplayName("before fails skips body and runs after")
    void beforeFailureSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> FailException.fail("before failed")))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.of("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("before aborts skips body and runs after")
    void beforeAbortsSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> AbortedException.abort("before aborted")))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.of("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(root.before().orElseThrow().isAborted()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("before skips skips body and runs after")
    void beforeSkipsSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> SkipException.skip("before skipped")))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.of("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(root.before().orElseThrow().isSkipped()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("before throws RuntimeException skips body and runs after")
    void beforeThrowsSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {
                    throw new RuntimeException("before threw");
                }))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.of("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().message()).contains("before threw");
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("before throws RuntimeException and after fails")
    void beforeThrowsAndAfterThrows() {
        var bodyCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {
                    throw new RuntimeException("before threw");
                }))
                .body(Step.of("child", context -> bodyCalls.incrementAndGet()))
                .after(Step.of("after", context -> FailException.fail("after failed")))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isFailed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
    }

    @Test
    @DisplayName("dependent body: first child fails skips remaining and runs after")
    void dependentBodyFirstBodyFailsSkipsRemainingAndRunsAfter() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> FailException.fail("child failed")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isFailed()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("dependent body: first child aborts, remaining still run")
    void dependentBodyFirstBodyAbortsRemainingStillRun() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> AbortedException.abort("child aborted")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isAborted()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("dependent body: first child skips skips remaining and runs after")
    void dependentBodyFirstBodySkipsSkipsRemainingAndRunsAfter() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> SkipException.skip("child skipped")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("dependent body: first child runtime exception skips remaining and runs after")
    void dependentBodyFirstBodyRuntimeExceptionSkipsRemainingAndRunsAfter() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> {
                            throw new RuntimeException("runtime error");
                        }))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isFailed()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("after fails aggregates FAILED")
    void afterFailsAggregatesFailed() {
        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> {}))
                        .child(Step.of("child-2", context -> {}))
                        .build())
                .after(Step.of("after", context -> FailException.fail("after failed")))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isPassed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isFailed()).isTrue();
    }

    @Test
    @DisplayName("no before, no after with dependent body passes")
    void noBeforeNoAfterWithBodyPasses() {
        var action = Static.builder("static")
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> {}))
                        .child(Step.of("child-2", context -> {}))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isPassed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
    }

    @Test
    @DisplayName("no before, after present with dependent body passes")
    void noBeforeAfterPresentWithBodyPasses() {
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> {}))
                        .child(Step.of("child-2", context -> {}))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isPassed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("no after, before present with dependent body passes")
    void noAfterBeforePresentWithBodyPasses() {
        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .child(Step.of("child-1", context -> {}))
                        .child(Step.of("child-2", context -> {}))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(body.children().get(0).isPassed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
    }

    @Test
    @DisplayName("no before, no after, no children passes")
    void noBeforeNoAfterNoChildrenPasses() {
        assertThatThrownBy(() -> Static.builder("static").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("single child with before and after passes")
    void singleBodyPasses() {
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Step.of("child-1", context -> {}))
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children().get(0).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("single child fails runs after")
    void singleBodyFailsRunsAfter() {
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Step.of("child-1", context -> FailException.fail("child failed")))
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(root.children().get(0).isFailed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: all pass")
    void independentBodyAllPass() {
        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> {}))
                        .child(Step.of("child-2", context -> {}))
                        .build())
                .after(Step.of("after", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(body.children().get(0).isPassed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
    }

    @Test
    @DisplayName("independent body: before fails still skips body and runs after")
    void independentBodyBeforeFailsStillSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> FailException.fail("before failed")))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.of("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.before().orElseThrow().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: first child fails all children still run")
    void independentBodyFirstBodyFailsAllChildrenStillRun() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> FailException.fail("child failed")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(body.children().get(0).isFailed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: first child aborts all children still run")
    void independentBodyFirstBodyAbortsAllChildrenStillRun() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> AbortedException.abort("child aborted")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isAborted()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(body.children().get(0).isAborted()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: first child skips all children still run")
    void independentBodyFirstBodySkipsAllChildrenStillRun() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> SkipException.skip("child skipped")))
                        .child(Step.of("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.of("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isSkipped()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(body.children().get(0).isSkipped()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: after fails aggregates FAILED")
    void independentBodyAfterFailsAggregatesFailed() {
        var action = Static.builder("static")
                .before(Step.of("before", context -> {}))
                .body(Sequence.builder("body")
                        .independent()
                        .child(Step.of("child-1", context -> {}))
                        .child(Step.of("child-2", context -> {}))
                        .build())
                .after(Step.of("after", context -> FailException.fail("after failed")))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().isPassed()).isTrue();
        assertThat(body.children().get(0).isPassed()).isTrue();
        assertThat(body.children().get(1).isPassed()).isTrue();
        assertThat(root.after().orElseThrow().isFailed()).isTrue();
    }
}
