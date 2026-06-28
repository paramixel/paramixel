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

package nonapi.org.paramixel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Step;

@DisplayName("ConcreteRunner exception handling")
class ConcreteRunnerExceptionHandlingTest {

    @Test
    @DisplayName("recoverable exception after root discovery marks root as failed with throwable")
    void recoverableExceptionAfterRootDiscoveryMarksRootAsFailedWithThrowable() throws Exception {
        var runner = (ConcreteRunner) Runner.builder().build();
        setIntField(runner, "schedulerQueueCapacity", 0);

        var result = runner.run(Step.of("root-step", context -> {}));
        var root = result.descriptor().orElseThrow();

        assertThat(result.isFailed()).isTrue();
        assertThat(root.isFailed()).isTrue();
        assertThat(root.throwable()).hasValueSatisfying(throwable -> {
            assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
            assertThat(throwable.getMessage()).contains("queueCapacity must be positive");
        });
    }

    @Test
    @DisplayName("onDiscoveryCompleted called exactly once when Scheduler constructor throws")
    void onDiscoveryCompletedCalledOnceWhenSchedulerThrows() throws Exception {
        var discoveryCount = new AtomicInteger();
        var lastRootWasNull = new AtomicBoolean();

        var listener = new Listener() {
            @Override
            public void onDiscoveryCompleted(Descriptor root) {
                discoveryCount.incrementAndGet();
                lastRootWasNull.set(root == null);
            }
        };

        var runner = (ConcreteRunner) Runner.builder().listener(listener).build();
        setIntField(runner, "schedulerQueueCapacity", 0);

        var result = runner.run(Step.of("test", ctx -> {}));

        assertThat(discoveryCount.get()).isEqualTo(1);
        assertThat(lastRootWasNull.get()).isFalse();
        assertThat(result.isFailed()).isTrue();
    }

    private static void setIntField(final Object target, final String fieldName, final int value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
