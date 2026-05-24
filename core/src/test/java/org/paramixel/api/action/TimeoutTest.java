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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.SkipException;

@DisplayName("Timeout action")
class TimeoutTest {

    @Test
    @DisplayName("child completes within timeout — child's status propagates")
    void childCompletesWithinTimeout() {
        var action = Timeout.of("fast-child")
                .timeout(Duration.ofSeconds(5))
                .child("step", ctx -> {})
                .resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).metadata().status()).isSameAs(Status.PASSED);
    }

    @Test
    @DisplayName("child exceeds timeout — action FAILED, child FAILED")
    void childExceedsTimeout() {
        var action = Timeout.of("slow-child")
                .timeout(Duration.ofMillis(50))
                .child("blocking-step", ctx -> {
                    Thread.sleep(5000);
                })
                .resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.metadata().status().isFailed()).isTrue();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).metadata().status().isFailed()).isTrue();
    }

    @Test
    @DisplayName("child fails on its own within timeout — child's FAILED propagates")
    void childFailsWithinTimeout() {
        var action = Timeout.of("failing-child")
                .timeout(Duration.ofSeconds(5))
                .child("fail-step", ctx -> {
                    throw new RuntimeException("intentional failure");
                })
                .resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.metadata().status().isFailed()).isTrue();
        assertThat(root.children().get(0).metadata().status().isFailed()).isTrue();
    }

    @Test
    @DisplayName("child throws SkipException within timeout — SKIPPED propagates")
    void childSkipsWithinTimeout() {
        var action = Timeout.of("skipping-child")
                .timeout(Duration.ofSeconds(5))
                .child("skip-step", ctx -> {
                    throw new SkipException("intentional skip");
                })
                .resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.metadata().status().isSkipped()).isTrue();
        assertThat(root.children().get(0).metadata().status().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("getName returns the supplied name")
    void getNameReturnsSuppliedName() {
        var action = Timeout.of("my-timeout")
                .timeout(Duration.ofSeconds(1))
                .child("step", ctx -> {})
                .resolve();

        assertThat(action.name()).isEqualTo("my-timeout");
    }

    @Test
    @DisplayName("child() and getTimeout() accessors return expected values")
    void accessorsReturnExpectedValues() {
        var child = Step.of("step", ctx -> {});
        var duration = Duration.ofSeconds(5);
        var action = Timeout.of("accessor-test").timeout(duration).child(child).resolve();

        assertThat(action.child()).isSameAs(child);
        assertThat(action.timeout()).isEqualTo(duration);
    }

    @Test
    @DisplayName("descriptor tree has 1 child discovered")
    void descriptorTreeHasOneChild() {
        var action = Timeout.of("tree-test")
                .timeout(Duration.ofSeconds(1))
                .child("step", ctx -> {})
                .resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.children()).hasSize(1);
    }
}
