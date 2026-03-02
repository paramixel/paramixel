/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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
}
