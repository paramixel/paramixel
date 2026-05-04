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

package org.paramixel.core.spi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.core.Action;
import org.paramixel.core.Configuration;
import org.paramixel.core.Listener;
import org.paramixel.core.action.Direct;

@DisplayName("DefaultRunner validation")
class DefaultRunnerValidationTest {

    @Test
    @DisplayName("routing executor rejects invalid public method arguments")
    void routingExecutorRejectsInvalidPublicMethodArguments() {
        AtomicReference<ExecutorService> executorRef = new AtomicReference<>();
        DefaultRunner runner = new DefaultRunner(Configuration.defaultProperties(), new Listener() {}, null);
        Action action = Direct.of("capture", context -> executorRef.set(context.getExecutorService()));

        runner.run(action);

        ExecutorService executorService = executorRef.get();
        assertThatThrownBy(() -> executorService.awaitTermination(-1, TimeUnit.MILLISECONDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be non-negative");
        assertThatThrownBy(() -> executorService.awaitTermination(1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("unit must not be null");
        assertThatThrownBy(() -> executorService.execute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
