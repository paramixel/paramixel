/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class ParamixelExecutionRuntimeTest {

    @Test
    public void submitNamed_setsThreadNameDuringTask() throws Exception {
        final AtomicReference<String> observed = new AtomicReference<>();
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(1)) {
            runtime.submitNamed(
                            "thread-name",
                            () -> observed.set(Thread.currentThread().getName()))
                    .get();
        }

        assertThat(observed.get()).isEqualTo("thread-name");
    }

    @Test
    public void createDefault_providesExecutorAndLimiter() {
        try (ParamixelExecutionRuntime runtime = ParamixelExecutionRuntime.createDefault()) {
            assertThat(runtime.executor()).isNotNull();
            assertThat(runtime.limiter()).isNotNull();
        }
    }

    @Test
    public void constructor_withCustomParallelism_configuresLimiterCorrectly() {
        // Test with parallelism higher than available processors (oversubscription)
        final int customParallelism = 100;
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(customParallelism)) {
            assertThat(runtime.limiter()).isNotNull();
            assertThat(runtime.limiter().cores()).isEqualTo(customParallelism);
            assertThat(runtime.limiter().classSlots()).isEqualTo(customParallelism);
            assertThat(runtime.limiter().totalSlots()).isEqualTo(customParallelism * 2);
        }
    }

    @Test
    public void constructor_withLowParallelism_configuresLimiterCorrectly() {
        // Test with low parallelism
        final int lowParallelism = 2;
        try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(lowParallelism)) {
            assertThat(runtime.limiter().cores()).isEqualTo(lowParallelism);
            assertThat(runtime.limiter().classSlots()).isEqualTo(lowParallelism);
            assertThat(runtime.limiter().totalSlots()).isEqualTo(lowParallelism * 2);
        }
    }
}
