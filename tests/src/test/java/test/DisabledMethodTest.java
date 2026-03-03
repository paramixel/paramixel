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
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that @Disabled on individual test methods skips those methods while allowing
 * other methods to execute.
 */
public class DisabledMethodTest {

    private static final AtomicInteger enabledTestCount = new AtomicInteger(0);
    private static final AtomicInteger disabledTestCount = new AtomicInteger(0);

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(1);
        collector.addArgument("test");
    }

    @Paramixel.Test
    public void enabledTest1(final @NonNull ArgumentContext context) {
        enabledTestCount.incrementAndGet();
    }

    @Paramixel.Test
    @Paramixel.Disabled("This test should be skipped")
    public void disabledTest(final @NonNull ArgumentContext context) {
        disabledTestCount.incrementAndGet();
        throw new IllegalStateException("Disabled test should not execute");
    }

    @Paramixel.Test
    public void enabledTest2(final @NonNull ArgumentContext context) {
        enabledTestCount.incrementAndGet();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(enabledTestCount.get()).isEqualTo(2);
        assertThat(disabledTestCount.get()).isEqualTo(0);
    }
}
