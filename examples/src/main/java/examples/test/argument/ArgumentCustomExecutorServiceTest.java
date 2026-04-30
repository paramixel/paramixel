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

package examples.test.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;
import org.paramixel.core.action.Sequential;

public class ArgumentCustomExecutorServiceTest {

    private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();
    private static ExecutorService CUSTOM_EXECUTOR_SERVICE;

    public static void main(String[] args) {
        ConsoleRunner.runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        INVOCATION_COUNT.set(0);
        CUSTOM_EXECUTOR_SERVICE = Executors.newFixedThreadPool(4);

        Action first = Direct.of("arg-0", context -> INVOCATION_COUNT.incrementAndGet());
        Action second = Direct.of("arg-1", context -> INVOCATION_COUNT.incrementAndGet());
        Action third = Direct.of("arg-2", context -> INVOCATION_COUNT.incrementAndGet());
        Action verify = Direct.of("verify", context -> {
            assertThat(INVOCATION_COUNT.get()).isEqualTo(3);
            assertThat(CUSTOM_EXECUTOR_SERVICE.isShutdown()).isFalse();
        });
        Action shutdown = Direct.of("shutdown", context -> {
            CUSTOM_EXECUTOR_SERVICE.shutdown();
            try {
                assertThat(CUSTOM_EXECUTOR_SERVICE.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for executor shutdown", e);
            }
        });

        return Sequential.of(
                "ArgumentCustomExecutorServiceTest",
                List.of(
                        Parallel.of("parallel-arguments", CUSTOM_EXECUTOR_SERVICE, List.of(first, second, third)),
                        verify,
                        shutdown));
    }
}
