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

package test.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

public abstract class LifecycleOrderInheritanceBase {

    protected static final List<String> actual = Collections.synchronizedList(new ArrayList<>());

    private static final AtomicInteger running = new AtomicInteger(0);

    private static final AtomicInteger maxRunning = new AtomicInteger(0);

    @Paramixel.Initialize
    @Paramixel.Order(1)
    public void initialize(final @NonNull ClassContext context) {
        actual.clear();
        running.set(0);
        maxRunning.set(0);
        actual.add("baseInitialize");
    }

    @Paramixel.BeforeAll
    @Paramixel.Order(20)
    public void baseBeforeAll(final @NonNull ArgumentContext context) {
        actual.add("baseBeforeAll");
    }

    @Paramixel.BeforeEach
    @Paramixel.Order(10)
    public void baseBeforeEach(final @NonNull ArgumentContext context) {
        actual.add("baseBeforeEach");
    }

    @Paramixel.AfterEach
    @Paramixel.Order(20)
    public void baseAfterEach(final @NonNull ArgumentContext context) {
        actual.add("baseAfterEach");
    }

    @Paramixel.AfterAll
    @Paramixel.Order(20)
    public void baseAfterAll(final @NonNull ArgumentContext context) {
        actual.add("baseAfterAll");
    }

    @Paramixel.Finalize
    @Paramixel.Order(20)
    public void baseFinalize(final @NonNull ClassContext context) {
        actual.add("baseFinalize");

        assertThat(maxRunning.get()).isEqualTo(1);
        assertThat(running.get()).isZero();

        final List<String> expected = new ArrayList<>();
        expected.add("baseInitialize");
        expected.add("subInitialize");
        expected.add("subBeforeAll");
        expected.add("baseBeforeAll");
        expected.add("baseBeforeEach");
        expected.add("subBeforeEach");
        expected.add("subOrdered1");
        expected.add("subAfterEach");
        expected.add("baseAfterEach");
        expected.add("baseBeforeEach");
        expected.add("subBeforeEach");
        expected.add("subOrdered2");
        expected.add("subAfterEach");
        expected.add("baseAfterEach");
        expected.add("baseBeforeEach");
        expected.add("subBeforeEach");
        expected.add("subOrdered3");
        expected.add("subAfterEach");
        expected.add("baseAfterEach");
        expected.add("baseBeforeEach");
        expected.add("subBeforeEach");
        expected.add("subUnorderedLast");
        expected.add("subAfterEach");
        expected.add("baseAfterEach");
        expected.add("subAfterAll");
        expected.add("baseAfterAll");
        expected.add("subFinalize");
        expected.add("baseFinalize");

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    protected final void recordSequential(final @NonNull String name, final @NonNull ArgumentContext context) {
        assertThat(context.getArgumentIndex()).isZero();

        final int nowRunning = running.incrementAndGet();
        maxRunning.accumulateAndGet(nowRunning, Math::max);

        try {
            assertThat(nowRunning).isEqualTo(1);
            actual.add(name);
            LockSupport.parkNanos(100_000_000L);
        } finally {
            running.decrementAndGet();
        }
    }
}
