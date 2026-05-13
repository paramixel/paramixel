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

package examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;

public class ParallelArgumentTest2 {

    private static final int ARGUMENT_COUNT = 5;
    private static final int PARALLELISM = 5;
    private static final int TEST_COUNT_PER_ARGUMENT = 3;

    private static final AtomicInteger beforeCount = new AtomicInteger();
    private static final AtomicInteger beforeArgumentCount = new AtomicInteger();
    private static final AtomicInteger testCount = new AtomicInteger();
    private static final AtomicInteger afterArgumentCount = new AtomicInteger();
    private static final AtomicInteger afterCount = new AtomicInteger();
    private static final AtomicInteger concurrentCount = new AtomicInteger();
    private static final AtomicInteger maxConcurrentCount = new AtomicInteger();

    public static void main(String[] args) {
        resetCounts();
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        var suiteName = "Parallel argument example 2";

        Action before = suiteBefore();
        Action parallel = parallel(suiteName);
        Action after = suiteAfter();

        return Container.builder(suiteName)
                .before(before)
                .child(parallel)
                .after(after)
                .build();
    }

    private static Action suiteBefore() {
        return Direct.builder("beforeArgument")
                .runnable(context -> beforeCount.incrementAndGet())
                .build();
    }

    private static Action parallel(String suiteName) {
        var parallelBuilder = Parallel.builder(suiteName).parallelism(PARALLELISM);
        for (int i = 0; i < ARGUMENT_COUNT; i++) {
            Action argument = argument("string-" + i);
            parallelBuilder.child(argument);
        }
        return parallelBuilder.build();
    }

    private static Action argument(String argumentValue) {
        Action before = argumentBefore();
        Action tests = tests();
        Action after = argumentAfter();

        return Container.builder(argumentValue)
                .before(before)
                .child(tests)
                .after(after)
                .build();
    }

    private static Action argumentBefore() {
        return Direct.builder("before")
                .runnable(context -> {
                    int current = concurrentCount.incrementAndGet();
                    maxConcurrentCount.accumulateAndGet(current, Math::max);
                    beforeArgumentCount.incrementAndGet();
                })
                .build();
    }

    private static Action tests() {
        var testsBuilder = Container.builder("tests")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        for (int i = 1; i <= TEST_COUNT_PER_ARGUMENT; i++) {
            Action test = test("test" + i);
            testsBuilder.child(test);
        }
        return testsBuilder.build();
    }

    private static Action test(String name) {
        return Direct.builder(name)
                .runnable(context -> testCount.incrementAndGet())
                .build();
    }

    private static Action argumentAfter() {
        return Direct.builder("after")
                .runnable(context -> {
                    concurrentCount.decrementAndGet();
                    afterArgumentCount.incrementAndGet();
                })
                .build();
    }

    private static Action suiteAfter() {
        return Direct.builder("afterArgument")
                .runnable(context -> {
                    afterCount.incrementAndGet();
                    assertThat(beforeCount.get()).isEqualTo(1);
                    assertThat(beforeArgumentCount.get()).isEqualTo(ARGUMENT_COUNT);
                    assertThat(testCount.get()).isEqualTo(ARGUMENT_COUNT * TEST_COUNT_PER_ARGUMENT);
                    assertThat(afterArgumentCount.get()).isEqualTo(ARGUMENT_COUNT);
                    assertThat(afterCount.get()).isEqualTo(1);
                    assertThat(concurrentCount.get()).isEqualTo(0);
                    assertThat(maxConcurrentCount.get()).isGreaterThan(0);
                    assertThat(maxConcurrentCount.get()).isLessThanOrEqualTo(PARALLELISM);
                })
                .build();
    }

    private static void resetCounts() {
        beforeCount.set(0);
        beforeArgumentCount.set(0);
        testCount.set(0);
        afterArgumentCount.set(0);
        afterCount.set(0);
        concurrentCount.set(0);
        maxConcurrentCount.set(0);
    }
}
