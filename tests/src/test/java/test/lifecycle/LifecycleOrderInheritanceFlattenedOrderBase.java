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

package test.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

public abstract class LifecycleOrderInheritanceFlattenedOrderBase {

    protected static final int ORDER_BASE_INITIALIZE = 11;

    protected static final int ORDER_SUB_INITIALIZE = 22;

    protected static final int ORDER_SUB_BEFORE_ALL = 10;

    protected static final int ORDER_BASE_BEFORE_ALL = 90;

    protected static final int ORDER_BASE_BEFORE_EACH = 33;

    protected static final int ORDER_SUB_BEFORE_EACH = 66;

    protected static final int ORDER_SUB_AFTER_EACH = 44;

    protected static final int ORDER_BASE_AFTER_EACH = 88;

    protected static final int ORDER_SUB_AFTER_ALL = 55;

    protected static final int ORDER_BASE_AFTER_ALL = 77;

    protected static final int ORDER_SUB_FINALIZE = 9;

    protected static final int ORDER_BASE_FINALIZE = 99;

    protected static final int ORDER_BASE_FIRST = 13;

    protected static final int ORDER_SUB_SECOND = 42;

    protected static final int ORDER_BASE_THIRD = 78;

    protected static final int ORDER_SUB_FOURTH = 91;

    protected static final List<String> actual = Collections.synchronizedList(new ArrayList<>());

    private static final AtomicInteger running = new AtomicInteger(0);

    private static final AtomicInteger maxRunning = new AtomicInteger(0);

    @Paramixel.AfterEach
    @Paramixel.Order(ORDER_BASE_AFTER_EACH)
    public void baseAfterEach(final @NonNull ArgumentContext context) {
        actual.add("baseAfterEach");
    }

    @Paramixel.AfterAll
    @Paramixel.Order(ORDER_BASE_AFTER_ALL)
    public void baseAfterAll(final @NonNull ArgumentContext context) {
        actual.add("baseAfterAll");
    }

    @Paramixel.BeforeEach
    @Paramixel.Order(ORDER_BASE_BEFORE_EACH)
    public void baseBeforeEach(final @NonNull ArgumentContext context) {
        actual.add("baseBeforeEach");
    }

    @Paramixel.BeforeAll
    @Paramixel.Order(ORDER_BASE_BEFORE_ALL)
    public void baseBeforeAll(final @NonNull ArgumentContext context) {
        actual.add("baseBeforeAll");
    }

    @Paramixel.Initialize
    @Paramixel.Order(ORDER_BASE_INITIALIZE)
    public void baseInitialize(final @NonNull ClassContext context) {
        actual.clear();
        running.set(0);
        maxRunning.set(0);
        actual.add("baseInitialize");

        assertThat(ORDER_BASE_INITIALIZE).isPositive();
        assertThat(ORDER_SUB_INITIALIZE).isPositive();
        assertThat(ORDER_SUB_BEFORE_ALL).isPositive();
        assertThat(ORDER_BASE_BEFORE_ALL).isPositive();
        assertThat(ORDER_BASE_BEFORE_EACH).isPositive();
        assertThat(ORDER_SUB_BEFORE_EACH).isPositive();
        assertThat(ORDER_SUB_AFTER_EACH).isPositive();
        assertThat(ORDER_BASE_AFTER_EACH).isPositive();
        assertThat(ORDER_SUB_AFTER_ALL).isPositive();
        assertThat(ORDER_BASE_AFTER_ALL).isPositive();
        assertThat(ORDER_SUB_FINALIZE).isPositive();
        assertThat(ORDER_BASE_FINALIZE).isPositive();

        assertThat(ORDER_BASE_FIRST).isPositive();
        assertThat(ORDER_SUB_SECOND).isPositive();
        assertThat(ORDER_BASE_THIRD).isPositive();
        assertThat(ORDER_SUB_FOURTH).isPositive();
        assertThat(Set.of(
                        ORDER_BASE_INITIALIZE,
                        ORDER_SUB_INITIALIZE,
                        ORDER_SUB_BEFORE_ALL,
                        ORDER_BASE_BEFORE_ALL,
                        ORDER_BASE_BEFORE_EACH,
                        ORDER_SUB_BEFORE_EACH,
                        ORDER_SUB_AFTER_EACH,
                        ORDER_BASE_AFTER_EACH,
                        ORDER_SUB_AFTER_ALL,
                        ORDER_BASE_AFTER_ALL,
                        ORDER_SUB_FINALIZE,
                        ORDER_BASE_FINALIZE,
                        ORDER_BASE_FIRST,
                        ORDER_SUB_SECOND,
                        ORDER_BASE_THIRD,
                        ORDER_SUB_FOURTH))
                .hasSize(16);
    }

    @Paramixel.Test
    @Paramixel.Order(ORDER_BASE_FIRST)
    public void baseOrdered2(final @NonNull ArgumentContext context) {
        recordSequential("baseOrdered2", context);
    }

    @Paramixel.Test
    @Paramixel.Order(ORDER_BASE_THIRD)
    public void baseOrdered5(final @NonNull ArgumentContext context) {
        recordSequential("baseOrdered5", context);
    }

    @Paramixel.Finalize
    @Paramixel.Order(ORDER_BASE_FINALIZE)
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
        expected.add("baseOrdered2");
        expected.add("subAfterEach");
        expected.add("baseAfterEach");

        expected.add("baseBeforeEach");
        expected.add("subBeforeEach");
        expected.add("subOrdered1");
        expected.add("subAfterEach");
        expected.add("baseAfterEach");

        expected.add("baseBeforeEach");
        expected.add("subBeforeEach");
        expected.add("baseOrdered5");
        expected.add("subAfterEach");
        expected.add("baseAfterEach");

        expected.add("baseBeforeEach");
        expected.add("subBeforeEach");
        expected.add("subOrdered4");
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
            LockSupport.parkNanos(50_000_000L);
        } finally {
            running.decrementAndGet();
        }
    }
}
