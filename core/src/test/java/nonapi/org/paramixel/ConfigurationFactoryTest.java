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

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;

@DisplayName("ConfigurationFactory")
class ConfigurationFactoryTest {

    @Test
    @DisplayName("classpathConfiguration returns non-null configuration")
    void classpathConfigurationReturnsNonNull() {
        var configuration = ConfigurationFactory.classpathConfiguration();

        assertThat(configuration).isNotNull();
    }

    @Test
    @DisplayName("systemConfiguration returns non-null configuration")
    void systemConfigurationReturnsNonNull() {
        var configuration = ConfigurationFactory.systemConfiguration();

        assertThat(configuration).isNotNull();
    }

    @Test
    @DisplayName("systemConfiguration includes non-paramixel keys")
    void systemConfigurationIncludesNonParamixelKeys() {
        var configuration = ConfigurationFactory.systemConfiguration();

        assertThat(configuration.getString("java.version")).isPresent();
        assertThat(configuration.getString("user.name")).isPresent();
    }

    @Test
    @DisplayName("defaultConfiguration includes non-paramixel keys")
    void defaultConfigurationIncludesNonParamixelKeys() {
        var configuration = ConfigurationFactory.defaultConfiguration();

        assertThat(configuration.getString("java.version")).isPresent();
        assertThat(configuration.getString("user.name")).isPresent();
    }

    @Test
    @DisplayName("defaultConfiguration contains RUNNER_PARALLELISM")
    void defaultConfigurationContainsRunnerParallelism() {
        var configuration = ConfigurationFactory.defaultConfiguration();

        assertThat(configuration.getString(Configuration.RUNNER_PARALLELISM)).isPresent();
        assertThat(configuration.getInteger(Configuration.RUNNER_PARALLELISM).orElseThrow())
                .isEqualTo(Runtime.getRuntime().availableProcessors());
    }

    @Test
    @DisplayName("defaultConfiguration returns consistent content under concurrent access")
    void defaultConfigurationReturnsConsistentContentUnderConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        var executor = Executors.newFixedThreadPool(threadCount);
        var ready = new CountDownLatch(threadCount);
        var go = new CountDownLatch(1);
        var results = Collections.synchronizedList(new ArrayList<Configuration>());
        var done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                results.add(ConfigurationFactory.defaultConfiguration());
                done.countDown();
            });
        }

        ready.await();
        go.countDown();
        done.await();
        executor.shutdown();

        assertThat(results).hasSize(threadCount);
        assertThat(results.stream()
                        .allMatch(c -> c.getString(Configuration.RUNNER_PARALLELISM)
                                .equals(results.get(0).getString(Configuration.RUNNER_PARALLELISM))))
                .isTrue();
    }

    @Test
    @DisplayName("defaultConfiguration contains SCHEDULER_QUEUE_CAPACITY")
    void defaultConfigurationContainsSchedulerQueueCapacity() {
        var configuration = ConfigurationFactory.defaultConfiguration();

        assertThat(configuration.getString(Configuration.SCHEDULER_QUEUE_CAPACITY))
                .isPresent();
        assertThat(configuration.getString(Configuration.SCHEDULER_QUEUE_CAPACITY))
                .contains("1024");
    }
}
