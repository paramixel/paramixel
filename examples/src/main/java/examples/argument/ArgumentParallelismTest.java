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

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Parallel;

public class ArgumentParallelismTest {

    private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        INVOCATION_COUNT.set(0);

        Action first = first();
        Action second = second();
        Action third = third();
        Action verify = verify();

        Action parallelArguments = parallelArguments(first, second, third);

        var suiteBuilder = Container.builder("ArgumentParallelismTest")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        suiteBuilder.child(parallelArguments);
        suiteBuilder.child(verify);
        return suiteBuilder.build();
    }

    private static Action first() {
        return Direct.builder("arg-0")
                .runnable(context -> INVOCATION_COUNT.incrementAndGet())
                .build();
    }

    private static Action second() {
        return Direct.builder("arg-1")
                .runnable(context -> INVOCATION_COUNT.incrementAndGet())
                .build();
    }

    private static Action third() {
        return Direct.builder("arg-2")
                .runnable(context -> INVOCATION_COUNT.incrementAndGet())
                .build();
    }

    private static Action parallelArguments(Action first, Action second, Action third) {
        var parallelBuilder = Parallel.builder("parallel-arguments").parallelism(3);
        parallelBuilder.child(first);
        parallelBuilder.child(second);
        parallelBuilder.child(third);
        return parallelBuilder.build();
    }

    private static Action verify() {
        return Direct.builder("verify")
                .runnable(context -> assertThat(INVOCATION_COUNT.get()).isEqualTo(3))
                .build();
    }
}
