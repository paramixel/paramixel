/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class ParallelArgumentTest {

    private static final int ARGUMENT_COUNT = 6;

    private static final AtomicInteger beforeAllCount = new AtomicInteger(0);
    private static final AtomicInteger testCount = new AtomicInteger(0);
    private static final AtomicInteger afterAllCount = new AtomicInteger(0);

    @Paramixel.ArgumentSupplier(parallelism = 2)
    public static Collection<String> arguments() {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < ARGUMENT_COUNT; i++) {
            values.add("String " + i);
        }
        return values;
    }

    @Paramixel.Initialize
    public void initialize(final ClassContext classContext) {
        assertThat(classContext).isNotNull();
    }

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext.getArgument()).isNotNull();
        beforeAllCount.incrementAndGet();
    }

    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext argumentContext) throws InterruptedException {
        assertThat(argumentContext.getArgument()).isNotNull();
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
        testCount.incrementAndGet();
    }

    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext argumentContext) throws InterruptedException {
        assertThat(argumentContext.getArgument()).isNotNull();
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
        testCount.incrementAndGet();
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext.getArgument()).isNotNull();
        afterAllCount.incrementAndGet();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext classContext) {
        assertThat(classContext).isNotNull();
        assertThat(beforeAllCount.get()).as("beforeAll count").isEqualTo(ARGUMENT_COUNT);
        assertThat(testCount.get()).as("test count").isEqualTo(ARGUMENT_COUNT * 2);
        assertThat(afterAllCount.get()).as("afterAll count").isEqualTo(ARGUMENT_COUNT);
    }
}
