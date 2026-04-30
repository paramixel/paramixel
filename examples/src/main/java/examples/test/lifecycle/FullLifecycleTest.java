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

package examples.test.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Sequential;

public class FullLifecycleTest {

    private static final int EXPECTED_ARGUMENT_COUNT = 3;
    private static final int EXPECTED_METHOD_COUNT_PER_ARGUMENT = 2;

    private static final AtomicInteger beforeActionCount = new AtomicInteger();
    private static final AtomicInteger beforeArgumentCount = new AtomicInteger();
    private static final AtomicInteger beforeTestCount = new AtomicInteger();
    private static final AtomicInteger invokeTestCount = new AtomicInteger();
    private static final AtomicInteger afterTestCount = new AtomicInteger();
    private static final AtomicInteger afterArgumentCount = new AtomicInteger();
    private static final AtomicInteger afterActionCount = new AtomicInteger();

    public static void main(String[] args) {
        ConsoleRunner.runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        resetCounts();
        return Lifecycle.of(
                "FullLifecycleTest",
                Direct.of("before", context -> beforeActionCount.incrementAndGet()),
                Sequential.of(
                        "arguments",
                        List.of(argumentAction("arg-0"), argumentAction("arg-1"), argumentAction("arg-2"))),
                Direct.of("after", context -> {
                    afterActionCount.incrementAndGet();
                    assertThat(beforeActionCount.get()).isEqualTo(1);
                    assertThat(beforeArgumentCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
                    assertThat(beforeTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(invokeTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(afterTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(afterArgumentCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
                    assertThat(afterActionCount.get()).isEqualTo(1);
                }));
    }

    private static Action argumentAction(String name) {
        return Lifecycle.of(
                name,
                Direct.of("before", context -> beforeArgumentCount.incrementAndGet()),
                Sequential.of(name + "-body", List.of(testAction("test-0"), testAction("test-1"))),
                Direct.of("after", context -> afterArgumentCount.incrementAndGet()));
    }

    private static Action testAction(String name) {
        return Lifecycle.of(
                name,
                Direct.of("before", context -> beforeTestCount.incrementAndGet()),
                Direct.of(name + "-body", context -> invokeTestCount.incrementAndGet()),
                Direct.of("after", context -> afterTestCount.incrementAndGet()));
    }

    private static void resetCounts() {
        beforeActionCount.set(0);
        beforeArgumentCount.set(0);
        beforeTestCount.set(0);
        invokeTestCount.set(0);
        afterTestCount.set(0);
        afterArgumentCount.set(0);
        afterActionCount.set(0);
    }
}
