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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies delivery of mixed-type arguments, including a {@code null} payload.
 */
public class CollectionMixedArgumentsTest {

    /** Counts the number of {@code null} arguments observed. */
    private static final AtomicInteger nullCount = new AtomicInteger(0);

    /** Counts the total number of invocations observed. */
    private static final AtomicInteger totalCount = new AtomicInteger(0);

    /**
     * Supplies a heterogeneous set of arguments.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArgument(null);
        collector.addArgument(Boolean.TRUE);
        collector.addArgument((short) 1);
        collector.addArgument((byte) 2);
        collector.addArgument('x');
        collector.addArgument(4);
        collector.addArgument(5L);
        collector.addArgument(6f);
        collector.addArgument(7d);
        collector.addArgument(new BigInteger("8"));
        collector.addArgument(new BigDecimal("9"));
        collector.addArgument("test");
        collector.addArgument(new Date());
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
        }
        totalCount.incrementAndGet();
    }

    /**
     * Verifies that all supplied arguments were observed and that exactly one was {@code null}.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(totalCount.get()).isEqualTo(13);
        assertThat(nullCount.get()).isEqualTo(1);
    }
}
