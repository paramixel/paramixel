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

package test.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies behavior when the argument supplier registers no arguments.
 *
 * <p>The test method throws if executed. The finalize hook asserts that the test method was never
 * invoked.
 */
public class NullArgumentTest {

    /** Set to {@code true} if the test method is invoked unexpectedly. */
    private static final AtomicBoolean TEST_EXECUTED = new AtomicBoolean(false);

    /**
     * Supplies no arguments.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        // Intentionally add no arguments.
    }

    /**
     * Should not be invoked because no arguments are supplied.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test(final @NonNull ArgumentContext context) {
        TEST_EXECUTED.set(true);
        throw new IllegalStateException("Should not be executed");
    }

    /**
     * Asserts that the test method was not executed.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(TEST_EXECUTED.get()).isFalse();
    }
}
