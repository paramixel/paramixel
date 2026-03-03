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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that {@code null} values can be supplied as arguments alongside non-null values.
 */
public class ArrayWithNullArgumentsTest {

    /** Tracks which non-null string arguments were observed. */
    private static final Set<String> seen = ConcurrentHashMap.newKeySet();

    /** Counts how many {@code null} arguments were observed. */
    private static final AtomicInteger nullCount = new AtomicInteger(0);

    /**
     * Supplies three arguments, two of which are {@code null}.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArguments((Object) null, "value", null);
    }

    /**
     * Records whether the current argument is {@code null}.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test(final @NonNull ArgumentContext context) {
        assertThat(context.getStore()).isNotNull();
        Object argument = context.getArgument();
        if (argument == null) {
            nullCount.incrementAndGet();
            return;
        }
        assertThat(argument).isInstanceOf(String.class);
        seen.add((String) argument);
    }

    /**
     * Verifies that two {@code null} values were observed and that the non-null value was recorded.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(nullCount.get()).isEqualTo(2);
        assertThat(seen).contains("value");
    }
}
