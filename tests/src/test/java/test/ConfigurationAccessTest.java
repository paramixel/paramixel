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

package test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.EngineContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that engine configuration values are accessible via EngineContext.
 *
 * <p>This test accesses configuration parameters through the context hierarchy and validates
 * that default values are returned when keys are absent.
 */
public class ConfigurationAccessTest {

    private static final AtomicInteger testsRun = new AtomicInteger(0);

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArgument("test");
    }

    @Paramixel.Test
    public void testEngineContextConfiguration(final @NonNull ArgumentContext context) {
        EngineContext engineContext = context.getClassContext().getEngineContext();
        assertThat(engineContext).isNotNull();
        assertThat(engineContext.getEngineId()).isEqualTo("paramixel");
        assertThat(engineContext.getStore()).isNotNull();

        // Test configuration value access
        String parallelism = engineContext.getConfigurationValue("paramixel.parallelism");
        assertThat(parallelism).isNotNull();

        // Test default value for missing key
        String missing = engineContext.getConfigurationValue("nonexistent.key", "default");
        assertThat(missing).isEqualTo("default");

        // Test null return for missing key without default
        String missingNoDefault = engineContext.getConfigurationValue("nonexistent.key");
        assertThat(missingNoDefault).isNull();

        testsRun.incrementAndGet();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(testsRun.get()).isEqualTo(1);
    }
}
