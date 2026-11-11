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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Runner;

/**
 * Pins the pool admission guarantees from {@code plans/pool-based-parallelism-7.0.0.md} §1: at most one
 * live child per permit run-wide, a permit held for the whole child subtree, and admission in declaration
 * order.
 */
@DisplayName("Pool admission order")
@org.junit.jupiter.api.Timeout(60)
class PoolAdmissionOrderTest {

    private static Runner runner(final int poolSize) {
        return Runner.builder()
                .configuration(Configuration.of(
                        Map.of("paramixel.parallelism.gate", String.valueOf(poolSize), Configuration.ANSI, "false")))
                .build();
    }

    @Test
    @DisplayName("a pool of size one admits children in declaration order")
    void sizeOneIsSerial() {
        var started = new CopyOnWriteArrayList<String>();
        var parallel = Parallel.parallel("serial", "gate");
        for (var name : List.of("a", "b", "c", "d")) {
            parallel.child(Step.step(name, context -> started.add(name)));
        }

        var result = runner(1).run(parallel.build());

        assertThat(result.status().isPassed()).isTrue();
        assertThat(started).containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("a permit is held for the whole subtree, so later children wait for the earlier ones")
    void permitIsHeldForTheWholeSubtree() {
        var started = new CopyOnWriteArrayList<String>();
        var release = new CountDownLatch(1);
        var blocked = new CountDownLatch(1);

        var gated = Step.step("gated", context -> {
            blocked.countDown();
            release.await();
        });
        var subtree = Scope.scope("subtree")
                .before(gated)
                .body(Step.step("body", context -> {}))
                .build();

        var action = Parallel.parallel("outer", "gate")
                .child(subtree)
                .child(Step.step("after-subtree", context -> started.add("after-subtree")))
                .build();

        var run = new Thread(() -> runner(1).run(action));
        run.start();
        try {
            assertThat(await(blocked)).isTrue();
            // The single permit is held by the gated subtree, so the sibling cannot have started.
            assertThat(started).isEmpty();
        } finally {
            release.countDown();
        }
        try {
            run.join(Duration.ofSeconds(30).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(started).containsExactly("after-subtree");
    }

    @Test
    @DisplayName("a pool of size N admits no more than N children at once")
    void poolBoundsLiveChildren() {
        var live = new CopyOnWriteArrayList<String>();
        var release = new CountDownLatch(1);
        var admitted = new CountDownLatch(2);

        var parallel = Parallel.parallel("bounded", "gate");
        for (var name : List.of("a", "b", "c", "d")) {
            parallel.child(Step.step(name, context -> {
                live.add(name);
                admitted.countDown();
                release.await();
            }));
        }

        var run = new Thread(() -> runner(2).run(parallel.build()));
        run.start();
        try {
            assertThat(await(admitted)).isTrue();
            // Only the first two declared children can be admitted while both hold a permit.
            assertThat(live).containsExactlyInAnyOrder("a", "b");
        } finally {
            release.countDown();
        }
        try {
            run.join(Duration.ofSeconds(30).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(live).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    private static boolean await(final CountDownLatch latch) {
        try {
            return latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
