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

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Action name")
class AbstractActionTest {

    @Test
    @DisplayName("getName returns the same value on repeated calls")
    void getNameReturnsSameValueOnRepeatedCalls() {
        var action = Step.of("stable-name-test", context -> {});

        var first = action.displayName();
        var second = action.displayName();

        assertThat(first).isNotNull();
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("name is immediately visible to concurrent readers after construction")
    void nameImmediatelyVisibleToConcurrentReaders() throws InterruptedException {
        int threadCount = 10;
        var executor = Executors.newFixedThreadPool(threadCount);
        var ready = new CountDownLatch(threadCount);
        var results = Collections.synchronizedList(new ArrayList<String>());
        var done = new CountDownLatch(threadCount);

        var action = Step.of("test-name", context -> {});

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                results.add(action.displayName());
                done.countDown();
            });
        }

        done.await();
        executor.shutdown();

        assertThat(results).hasSize(threadCount);
        assertThat(results).allSatisfy(n -> assertThat(n).isEqualTo("test-name"));
    }
}
