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
 * Verifies that static @BeforeAll and @AfterAll lifecycle methods are invoked correctly.
 *
 * <p>According to the spec, @BeforeAll and @BeforeAll methods may be static, unlike other
 * lifecycle hooks which must be instance methods.
 */
public class StaticLifecycleMethodsTest {

    private static final AtomicInteger staticBeforeAllCount = new AtomicInteger(0);
    private static final AtomicInteger staticAfterAllCount = new AtomicInteger(0);
    private static final AtomicInteger instanceBeforeAllCount = new AtomicInteger(0);
    private static final AtomicInteger instanceAfterAllCount = new AtomicInteger(0);

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(1);
        collector.addArguments("arg1", "arg2");
    }

    @Paramixel.BeforeAll
    public static void staticBeforeAll(final @NonNull ArgumentContext context) {
        staticBeforeAllCount.incrementAndGet();
    }

    @Paramixel.BeforeAll
    public void instanceBeforeAll(final @NonNull ArgumentContext context) {
        instanceBeforeAllCount.incrementAndGet();
    }

    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
    }

    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isNotNull();
    }

    @Paramixel.AfterAll
    public static void staticAfterAll(final @NonNull ArgumentContext context) {
        staticAfterAllCount.incrementAndGet();
    }

    @Paramixel.AfterAll
    public void instanceAfterAll(final @NonNull ArgumentContext context) {
        instanceAfterAllCount.incrementAndGet();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        // 2 arguments, so each BeforeAll/AfterAll should be called twice
        assertThat(staticBeforeAllCount.get()).isEqualTo(2);
        assertThat(instanceBeforeAllCount.get()).isEqualTo(2);
        assertThat(staticAfterAllCount.get()).isEqualTo(2);
        assertThat(instanceAfterAllCount.get()).isEqualTo(2);
    }
}
