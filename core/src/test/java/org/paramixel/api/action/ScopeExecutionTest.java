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
import org.paramixel.api.exception.AbortException;
import org.paramixel.api.exception.FailException;
import org.paramixel.api.exception.SkipException;

@DisplayName("Scope execution")
class ScopeExecutionTest {

    @Test
    @DisplayName("all pass with before, dependent body, and after")
    void allPass() {
        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {}))
                        .child(Step.step("child-2", context -> {}))
                        .build())
                .after(Step.step("after", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isPassed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("before fails skips body and runs after")
    void beforeFailureSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    throw FailException.fail("before failed");
                }))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.step("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("before aborts skips body and runs after")
    void beforeAbortsSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    throw AbortException.abort("before aborted");
                }))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.step("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isAborted()).isTrue();
        assertThat(root.before().orElseThrow().status().isAborted()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("before skips skips body and runs after")
    void beforeSkipsSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    throw SkipException.skip("before skipped");
                }))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.step("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isSkipped()).isTrue();
        assertThat(root.before().orElseThrow().status().isSkipped()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("before throws RuntimeException skips body and runs after")
    void beforeThrowsSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    throw new RuntimeException("before threw");
                }))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.step("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().message()).contains("before threw");
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("before throws RuntimeException and after fails")
    void beforeThrowsAndAfterThrows() {
        var bodyCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    throw new RuntimeException("before threw");
                }))
                .body(Step.step("child", context -> bodyCalls.incrementAndGet()))
                .after(Step.step("after", context -> {
                    throw FailException.fail("after failed");
                }))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isFailed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
    }

    @Test
    @DisplayName("dependent body: first child fails skips remaining and runs after")
    void dependentBodyFirstBodyFailsSkipsRemainingAndRunsAfter() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {
                            throw FailException.fail("child failed");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isFailed()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("dependent body: first child aborts remaining still run")
    void dependentBodyFirstBodyAbortsRemainingStillRun() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {
                            throw AbortException.abort("child aborted");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isAborted()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isAborted()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("dependent body: first child skips skips remaining and runs after")
    void dependentBodyFirstBodySkipsSkipsRemainingAndRunsAfter() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {
                            throw SkipException.skip("child skipped");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isSkipped()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("dependent body: first child runtime exception skips remaining and runs after")
    void dependentBodyFirstBodyRuntimeExceptionSkipsRemainingAndRunsAfter() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {
                            throw new RuntimeException("runtime error");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isFailed()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("after fails aggregates FAILED")
    void afterFailsAggregatesFailed() {
        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {}))
                        .child(Step.step("child-2", context -> {}))
                        .build())
                .after(Step.step("after", context -> {
                    throw FailException.fail("after failed");
                }))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isPassed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isFailed()).isTrue();
    }

    @Test
    @DisplayName("no before, no after with dependent body passes")
    void noBeforeNoAfterWithBodyPasses() {
        var action = Scope.scope("scope")
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {}))
                        .child(Step.step("child-2", context -> {}))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isPassed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("no before, after present with dependent body passes")
    void noBeforeAfterPresentWithBodyPasses() {
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {}))
                        .child(Step.step("child-2", context -> {}))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isPassed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("no after, before present with dependent body passes")
    void noAfterBeforePresentWithBodyPasses() {
        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .child(Step.step("child-1", context -> {}))
                        .child(Step.step("child-2", context -> {}))
                        .build())
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(body.children().get(0).status().isPassed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("no before, no after, no children passes")
    void noBeforeNoAfterNoChildrenPasses() {
        assertThatThrownBy(() -> Scope.scope("scope").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("body action must be configured");
    }

    @Test
    @DisplayName("single child with before and after passes")
    void singleBodyPasses() {
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Step.step("child-1", context -> {}))
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children().get(0).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("single child fails runs after")
    void singleBodyFailsRunsAfter() {
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Step.step("child-1", context -> {
                    throw FailException.fail("child failed");
                }))
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(root.children().get(0).status().isFailed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: all pass")
    void independentBodyAllPass() {
        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> {}))
                        .child(Step.step("child-2", context -> {}))
                        .build())
                .after(Step.step("after", context -> {}))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isPassed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(body.children().get(0).status().isPassed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
    }

    @Test
    @DisplayName("independent body: before fails still skips body and runs after")
    void independentBodyBeforeFailsStillSkipsBodyAndRunsAfter() {
        var bodyCalls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {
                    throw FailException.fail("before failed");
                }))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> bodyCalls.incrementAndGet()))
                        .child(Step.step("child-2", context -> bodyCalls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.before().orElseThrow().status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isSkipped()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(bodyCalls.get()).isZero();
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: first child fails all children still run")
    void independentBodyFirstBodyFailsAllChildrenStillRun() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> {
                            throw FailException.fail("child failed");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(body.children().get(0).status().isFailed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: first child aborts all children still run")
    void independentBodyFirstBodyAbortsAllChildrenStillRun() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> {
                            throw AbortException.abort("child aborted");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isAborted()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(body.children().get(0).status().isAborted()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: first child skips all children still run")
    void independentBodyFirstBodySkipsAllChildrenStillRun() {
        var child2Calls = new AtomicInteger();
        var afterCalls = new AtomicInteger();

        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> {
                            throw SkipException.skip("child skipped");
                        }))
                        .child(Step.step("child-2", context -> child2Calls.incrementAndGet()))
                        .build())
                .after(Step.step("after", context -> afterCalls.incrementAndGet()))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isSkipped()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(body.children().get(0).status().isSkipped()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isPassed()).isTrue();
        assertThat(child2Calls.get()).isEqualTo(1);
        assertThat(afterCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("independent body: after fails aggregates FAILED")
    void independentBodyAfterFailsAggregatesFailed() {
        var action = Scope.scope("scope")
                .before(Step.step("before", context -> {}))
                .body(Sequential.sequential("body")
                        .independent()
                        .child(Step.step("child-1", context -> {}))
                        .child(Step.step("child-2", context -> {}))
                        .build())
                .after(Step.step("after", context -> {
                    throw FailException.fail("after failed");
                }))
                .build();

        var root = Runner.builder().build().run(action).descriptor().orElseThrow();

        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        var body = root.children().get(0);
        assertThat(body.children()).hasSize(2);
        assertThat(root.before().orElseThrow().status().isPassed()).isTrue();
        assertThat(body.children().get(0).status().isPassed()).isTrue();
        assertThat(body.children().get(1).status().isPassed()).isTrue();
        assertThat(root.after().orElseThrow().status().isFailed()).isTrue();
    }
}
