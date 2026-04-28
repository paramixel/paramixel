/*
 * Copyright (c) 2026-present Douglas Hoard
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Sequential;
import test.util.RunnerSupport;

public class FullLifecycleTest {

    private static final int EXPECTED_ARGUMENT_COUNT = 3;
    private static final int EXPECTED_METHOD_COUNT_PER_ARGUMENT = 2;

    private static final AtomicInteger setUpActionCount = new AtomicInteger();
    private static final AtomicInteger setUpArgumentCount = new AtomicInteger();
    private static final AtomicInteger setUpTestCount = new AtomicInteger();
    private static final AtomicInteger invokeTestCount = new AtomicInteger();
    private static final AtomicInteger tearDownTestCount = new AtomicInteger();
    private static final AtomicInteger tearDownArgumentCount = new AtomicInteger();
    private static final AtomicInteger tearDownActionCount = new AtomicInteger();

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        resetCounts();
        return Lifecycle.of(
                "FullLifecycleTest",
                context -> setUpActionCount.incrementAndGet(),
                Sequential.of(
                        "arguments",
                        List.of(argumentAction("arg-0"), argumentAction("arg-1"), argumentAction("arg-2"))),
                context -> {
                    tearDownActionCount.incrementAndGet();
                    assertThat(setUpActionCount.get()).isEqualTo(1);
                    assertThat(setUpArgumentCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
                    assertThat(setUpTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(invokeTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(tearDownTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(tearDownArgumentCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
                    assertThat(tearDownActionCount.get()).isEqualTo(1);
                });
    }

    private static Action argumentAction(String name) {
        return Lifecycle.of(
                name,
                context -> setUpArgumentCount.incrementAndGet(),
                Sequential.of(name + "-body", List.of(testAction("test-0"), testAction("test-1"))),
                context -> tearDownArgumentCount.incrementAndGet());
    }

    private static Action testAction(String name) {
        return Lifecycle.of(
                name,
                context -> setUpTestCount.incrementAndGet(),
                Direct.of(name + "-body", context -> invokeTestCount.incrementAndGet()),
                context -> tearDownTestCount.incrementAndGet());
    }

    private static void resetCounts() {
        setUpActionCount.set(0);
        setUpArgumentCount.set(0);
        setUpTestCount.set(0);
        invokeTestCount.set(0);
        tearDownTestCount.set(0);
        tearDownArgumentCount.set(0);
        tearDownActionCount.set(0);
    }

    public static void main(String[] args) {
        RunnerSupport.runAction(actionFactory());
    }
}
