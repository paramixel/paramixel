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
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that a test class without an @ArgumentsCollector method receives a single
 * null argument and executes test methods once.
 *
 * <p>This validates the spec requirement that classes without arguments collectors
 * are executed with a single null argument.
 */
public class NoArgumentsCollectorTest {

    private static final AtomicInteger testCount = new AtomicInteger(0);
    private static final AtomicInteger beforeAllCount = new AtomicInteger(0);
    private static final AtomicInteger afterAllCount = new AtomicInteger(0);

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        beforeAllCount.incrementAndGet();
        // Argument should be null when no collector is present
        assertThat(context.getArgument()).isNull();
    }

    @Paramixel.Test
    public void testWithNullArgument(final @NonNull ArgumentContext context) {
        testCount.incrementAndGet();
        // Argument should be null when no collector is present
        assertThat(context.getArgument()).isNull();
        assertThat(context.getArgumentIndex()).isEqualTo(0);
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        afterAllCount.incrementAndGet();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        // Should execute exactly once with null argument
        assertThat(beforeAllCount.get()).isEqualTo(1);
        assertThat(testCount.get()).isEqualTo(1);
        assertThat(afterAllCount.get()).isEqualTo(1);
    }
}
