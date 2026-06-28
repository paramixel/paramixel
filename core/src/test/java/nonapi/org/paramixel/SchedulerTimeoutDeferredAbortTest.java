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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Listener;
import org.paramixel.api.Runner;
import org.paramixel.api.action.Sequential;
import org.paramixel.api.action.Step;
import org.paramixel.api.action.Timeout;

@DisplayName("Scheduler timeout deferred abort")
class SchedulerTimeoutDeferredAbortTest {

    @Test
    @DisplayName("timeout unblocks deferred child coordinator listener bracket")
    void timeoutUnblocksDeferredChildCoordinatorListenerBracket() throws Exception {
        var blockingLeafStarted = new CountDownLatch(1);
        var bodySequentialAfter = new CountDownLatch(1);
        var bodySequentialAfterWasTerminal = new AtomicBoolean();
        var listener = new Listener() {
            @Override
            public void onAfterExecution(final Descriptor descriptor) {
                if ("body-sequential".equals(descriptor.action().displayName())) {
                    bodySequentialAfterWasTerminal.set(descriptor.isCompleted());
                    bodySequentialAfter.countDown();
                }
            }
        };
        var action = Timeout.builder("timeout")
                .timeout(Duration.ofMillis(200))
                .body(Sequential.builder("body-sequential")
                        .child(Step.of("blocking-leaf", context -> {
                            blockingLeafStarted.countDown();
                            try {
                                Thread.sleep(5_000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }))
                        .child(Step.of("after-timeout", context -> {}))
                        .build())
                .build();
        var configuration = Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "2"));

        var result = Runner.builder()
                .configuration(configuration)
                .listener(listener)
                .build()
                .run(action);

        assertThat(blockingLeafStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(result.descriptor().orElseThrow().isFailed()).isTrue();
        assertThat(bodySequentialAfter.await(5, TimeUnit.SECONDS))
                .as("started deferred child coordinator should close its listener bracket")
                .isTrue();
        assertThat(bodySequentialAfterWasTerminal).isTrue();
    }
}
