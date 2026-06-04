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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Result;
import org.paramixel.api.Runner;

@DisplayName("Parallel depth 10")
class ParallelDepthTest {

    private static final int DEPTH = 10;

    private static final int BRANCHING_FACTOR = 2;

    private static final int LEAF_COUNT = (int) Math.pow(BRANCHING_FACTOR, DEPTH);

    private static Action buildTree(final int remainingDepth, final AtomicInteger executionCount) {
        if (remainingDepth == 0) {
            return Step.of("leaf", context -> executionCount.incrementAndGet());
        }
        var depthFromRoot = DEPTH - remainingDepth;
        var builder = Parallel.builder("depth-" + depthFromRoot).parallelism(BRANCHING_FACTOR);
        for (var i = 0; i < BRANCHING_FACTOR; i++) {
            builder.child(buildTree(remainingDepth - 1, executionCount));
        }
        return builder.build();
    }

    @Test
    @DisplayName("full-branching tree of depth 10 completes and executes all leaves")
    void fullBranchingTreeCompletes() {
        var executionCount = new AtomicInteger();
        var root = buildTree(DEPTH, executionCount);

        var result = Runner.defaultRunner().run(root);

        assertThat(result.isPassed()).isTrue();
        assertThat(executionCount.get()).isEqualTo(LEAF_COUNT);
    }

    @Test
    @DisplayName("full-branching tree of depth 10 completes with constrained scheduler")
    void fullBranchingTreeConstrainedScheduler() {
        var executionCount = new AtomicInteger();
        var root = buildTree(DEPTH, executionCount);

        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "2")))
                .build();

        var result = assertTimeoutPreemptively(Duration.ofMinutes(2), () -> runner.run(root));

        assertThat(((Result) result).isPassed()).isTrue();
        assertThat(executionCount.get()).isEqualTo(LEAF_COUNT);
    }

    @Test
    @DisplayName("full-branching tree of depth 10 completes with bounded parallelism and constrained scheduler")
    void fullBranchingTreeBoundedParallelism() {
        var executionCount = new AtomicInteger();
        var root = buildTree(DEPTH, executionCount);

        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "2")))
                .build();

        var result = assertTimeoutPreemptively(Duration.ofMinutes(2), () -> runner.run(root));

        assertThat(((Result) result).isPassed()).isTrue();
        assertThat(executionCount.get()).isEqualTo(LEAF_COUNT);
    }
}
