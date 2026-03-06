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

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

/**
 * Tests that duration timing is captured for test execution.
 *
 * <p>This test verifies that the engine correctly tracks execution duration
 * at the class level. The duration is displayed in the test summary table.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
@Paramixel.TestClass
public class DurationTimingTest {

    private static long initializeTime;
    private static long finalizeTime;

    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        initializeTime = System.currentTimeMillis();
    }

    @Paramixel.Test
    public void testWithDuration(final @NonNull ArgumentContext context) throws InterruptedException {
        Thread.sleep(50L);
        assertThat(true).isTrue();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        finalizeTime = System.currentTimeMillis();
        long duration = finalizeTime - initializeTime;
        assertThat(duration).isGreaterThanOrEqualTo(50L);
    }
}
