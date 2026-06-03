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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Runner;

@DisplayName("Parallel action")
class ParallelTest {

    @Test
    @DisplayName("child RuntimeException preserves instance and stack trace")
    void childRuntimeExceptionPreservesInstance() {
        var exception = new RuntimeException("intentional failure");
        var action = Parallel.builder("failing-parallel")
                .child(Step.of("fail-step", context -> {
                    throw exception;
                }))
                .build();
        var result = Runner.builder().build().run(action);
        var root = result.descriptor().orElseThrow();

        assertThat(root.isFailed()).isTrue();
        var child = root.children().get(0);
        assertThat(child.isFailed()).isTrue();
        assertThat(child.throwable()).isPresent();
        assertThat(child.throwable().get()).isSameAs(exception);
    }
}
