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

import java.util.Optional;
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

        var result = runner.run(org.paramixel.api.action.Parallel.parallel("p", "undefined-pool")
                .child(Step.step("root-step", context -> {}))
                .build());
        var root = result.descriptor().orElseThrow();

        assertThat(result.status().isFailed()).isTrue();
        assertThat(root.status().isFailed()).isTrue();
        assertThat(root.status().throwable()).hasValueSatisfying(throwable -> {
            assertThat(throwable).isInstanceOf(org.paramixel.api.exception.ConfigurationException.class);
            assertThat(throwable.getMessage()).contains("undefined-pool");
        });
    }

    @Test
    @DisplayName("onDiscoveryCompleted called exactly once when Scheduler constructor throws")
    void onDiscoveryCompletedCalledOnceWhenSchedulerThrows() throws Exception {
        var discoveryCount = new AtomicInteger();
        var lastRootWasNull = new AtomicBoolean();

        var listener = new Listener() {
            @Override
            public void onDiscoveryCompleted(final Optional<Descriptor> root) {
                discoveryCount.incrementAndGet();
                lastRootWasNull.set(root.isEmpty());
            }
        };

        var runner = (ConcreteRunner) Runner.builder().listener(listener).build();

        var result = runner.run(org.paramixel.api.action.Parallel.parallel("p", "undefined-pool")
                .child(Step.step("test", ctx -> {}))
                .build());

        assertThat(discoveryCount.get()).isEqualTo(1);
        assertThat(lastRootWasNull.get()).isFalse();
        assertThat(result.status().isFailed()).isTrue();
    }
}
