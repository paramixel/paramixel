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

package examples.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.AsyncScheduler;
import org.paramixel.core.Context;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Result;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Parallel;

public class ArgumentCustomAsyncSchedulerTest {

    private static final TrackingScheduler SCHEDULER = new TrackingScheduler();
    private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        SCHEDULER.reset();
        INVOCATION_COUNT.set(0);

        Action first = first();
        Action second = second();
        Action third = third();
        Action verify = verify();

        Action parallel = parallel(first, second, third);

        Action main = main(parallel, verify);

        Action before = before();

        return Container.builder("ArgumentCustomAsyncSchedulerTest")
                .before(before)
                .child(main)
                .build();
    }

    private static Action first() {
        return Direct.builder("arg-0")
                .execute(context -> INVOCATION_COUNT.incrementAndGet())
                .build();
    }

    private static Action second() {
        return Direct.builder("arg-1")
                .execute(context -> {
                    context.runAsync(Noop.of("nested-async")).join();
                    INVOCATION_COUNT.incrementAndGet();
                })
                .build();
    }

    private static Action third() {
        return Direct.builder("arg-2")
                .execute(context -> INVOCATION_COUNT.incrementAndGet())
                .build();
    }

    private static Action parallel(Action first, Action second, Action third) {
        return Parallel.builder("parallel-arguments")
                .scheduler(SCHEDULER)
                .child(first)
                .child(second)
                .child(third)
                .build();
    }

    private static Action verify() {
        return Direct.builder("verify")
                .execute(context -> {
                    assertThat(INVOCATION_COUNT.get()).isEqualTo(3);
                    assertThat(SCHEDULER.scheduled()).isEqualTo(4);
                })
                .build();
    }

    private static Action main(Action parallel, Action verify) {
        var mainBuilder = Container.builder("main")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        mainBuilder.child(parallel);
        mainBuilder.child(verify);
        return mainBuilder.build();
    }

    private static Action before() {
        return Noop.of("before");
    }

    private static final class TrackingScheduler implements AsyncScheduler {

        private final AtomicInteger scheduled = new AtomicInteger();

        @Override
        public CompletableFuture<Result> runAsync(Action action, Context context) {
            scheduled.incrementAndGet();
            try {
                return CompletableFuture.completedFuture(action.execute(context));
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        }

        private int scheduled() {
            return scheduled.get();
        }

        private void reset() {
            scheduled.set(0);
        }
    }
}
