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
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;
import org.paramixel.api.Status;
import org.paramixel.api.exception.FailException;

@DisplayName("Repeat action")
class RepeatTest {

    @Test
    @DisplayName("dependent repeat: all repetitions pass")
    void dependentRepeatAllPass() {
        var counter = new AtomicInteger();
        var action = Repeat.of("dependent-all-pass")
                .count(3)
                .child("step", ctx -> counter.incrementAndGet())
                .resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("dependent repeat: second repetition fails, remaining skipped")
    void dependentRepeatSecondFails() {
        var counter = new AtomicInteger();
        var action = Repeat.of("dependent-second-fails")
                .count(3)
                .child("step", ctx -> {
                    int count = counter.incrementAndGet();
                    if (count == 2) {
                        FailException.fail("intentional failure");
                    }
                })
                .resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();
        var children = root.children();

        assertThat(root.metadata().status().isFailed()).isTrue();
        assertThat(children.get(0).metadata().status()).isSameAs(Status.PASSED);
        assertThat(children.get(1).metadata().status().isFailed()).isTrue();
        assertThat(children.get(2).metadata().status()).isSameAs(Status.SKIPPED);
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("independent repeat: one failure, all still run")
    void independentRepeatOneFailure() {
        var counter = new AtomicInteger();
        var action = Repeat.of("independent-one-fails")
                .count(3)
                .independent()
                .child("step", ctx -> {
                    int count = counter.incrementAndGet();
                    if (count == 2) {
                        FailException.fail("intentional failure");
                    }
                })
                .resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.metadata().status().isFailed()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("single repetition (count=1) passes")
    void singleRepetitionPasses() {
        var action = Repeat.of("single").count(1).child("step", ctx -> {}).resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.metadata().status()).isSameAs(Status.PASSED);
        assertThat(root.children()).hasSize(1);
    }

    @Test
    @DisplayName("getName returns the supplied name")
    void getNameReturnsSuppliedName() {
        var action = Repeat.of("my-repeat").child("step", ctx -> {}).resolve();

        assertThat(action.name()).isEqualTo("my-repeat");
    }

    @Test
    @DisplayName("accessors return expected values")
    void accessorsReturnExpectedValues() {
        var child = Step.of("step", ctx -> {});
        var action = Repeat.of("repeat").count(5).independent().child(child).resolve();

        assertThat(action.child()).isSameAs(child);
        assertThat(action.repeatCount()).isEqualTo(5);
        assertThat(action.isIndependent()).isTrue();
        assertThat(action.isDependent()).isFalse();
    }

    @Test
    @DisplayName("dependent defaults: isDependent is true, isIndependent is false")
    void dependentDefaults() {
        var action = Repeat.of("repeat").child("step", ctx -> {}).resolve();

        assertThat(action.isDependent()).isTrue();
        assertThat(action.isIndependent()).isFalse();
    }

    @Test
    @DisplayName("descriptor tree has N children for N repetitions")
    void descriptorTreeHasNChildren() {
        var action = Repeat.of("repeat-tree").count(4).child("step", ctx -> {}).resolve();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.children()).hasSize(4);
    }
}
