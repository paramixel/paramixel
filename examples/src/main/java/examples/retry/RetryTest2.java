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

package examples.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.support.Retry;
import org.paramixel.core.support.Retry.Policy;

public class RetryTest2 {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        var counter = new AtomicInteger(0);
        return Direct.builder("retry-example")
                .runnable(context -> {
                    Retry.Result result = Retry.of(Policy.fixed(Duration.ofMillis(100), Duration.ofSeconds(1)))
                            .onRetry((attempt, cause) ->
                                    System.out.println("Retry attempt " + attempt + " after: " + cause.getMessage()))
                            .run(() -> {
                                int attempt = counter.incrementAndGet();
                                if (attempt < 3) {
                                    throw new RuntimeException("flaky failure on attempt " + attempt);
                                }
                            });

                    assertThat(result.isPass()).isTrue();
                    assertThat(result.getAttemptCount()).isEqualTo(3);
                })
                .build();
    }
}
