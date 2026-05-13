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

package examples.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class FullLifecycleTest {

    record TestState(String name) {}

    private static final int EXPECTED_ARGUMENT_COUNT = 3;
    private static final int EXPECTED_METHOD_COUNT_PER_ARGUMENT = 2;

    private static final AtomicInteger beforeCount = new AtomicInteger();
    private static final AtomicInteger beforeArgumentCount = new AtomicInteger();
    private static final AtomicInteger beforeTestCount = new AtomicInteger();
    private static final AtomicInteger invokeTestCount = new AtomicInteger();
    private static final AtomicInteger afterTestCount = new AtomicInteger();
    private static final AtomicInteger afterArgumentCount = new AtomicInteger();
    private static final AtomicInteger afterCount = new AtomicInteger();

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        resetCounts();

        Action suiteBefore = suiteBefore();
        Action arguments = arguments();
        Action suiteAfter = suiteAfter();

        return Container.builder("FullLifecycleTest")
                .before(suiteBefore)
                .child(arguments)
                .after(suiteAfter)
                .build();
    }

    private static Action suiteBefore() {
        return Direct.builder("before")
                .runnable(context -> {
                    beforeCount.incrementAndGet();
                    context.getStore().put("suite", new TestState("FullLifecycleTest"));
                })
                .build();
    }

    private static Action arguments() {
        var argumentsBuilder = Container.builder("arguments")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        for (int i = 0; i < EXPECTED_ARGUMENT_COUNT; i++) {
            Action argument = argument("arg-" + i);
            argumentsBuilder.child(argument);
        }
        return argumentsBuilder.build();
    }

    private static Action argument(String name) {
        Action before = argumentBefore(name);
        Action body = argumentBody(name);
        Action after = argumentAfter();

        return Container.builder(name).before(before).child(body).after(after).build();
    }

    private static Action argumentBefore(String name) {
        return Direct.builder("before")
                .runnable(context -> {
                    beforeArgumentCount.incrementAndGet();
                    context.getStore().put("argument", new TestState(name));
                })
                .build();
    }

    private static Action argumentBody(String name) {
        var bodyBuilder = Container.builder(name + "-body")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        for (int i = 0; i < EXPECTED_METHOD_COUNT_PER_ARGUMENT; i++) {
            Action test = test("test-" + i, name);
            bodyBuilder.child(test);
        }
        return bodyBuilder.build();
    }

    private static Action test(String name, String argumentName) {
        Action before = testBefore();
        Action body = testBody(name, argumentName);
        Action after = testAfter();

        return Container.builder(name).before(before).child(body).after(after).build();
    }

    private static Action testBefore() {
        return Direct.builder("before")
                .runnable(context -> {
                    beforeTestCount.incrementAndGet();
                    context.getStore().put("test-before-ran", true);
                })
                .build();
    }

    private static Action testBody(String name, String argumentName) {
        return Direct.builder(name + "-body")
                .runnable(context -> {
                    invokeTestCount.incrementAndGet();

                    assertThat(context.findParent()).isPresent();
                    assertThat(context.getParent()).isNotNull();

                    TestState argumentState = context.getAncestor("../../../")
                            .getStore()
                            .get("argument", TestState.class)
                            .orElseThrow();
                    assertThat(argumentState.name()).isEqualTo(argumentName);
                })
                .build();
    }

    private static Action testAfter() {
        return Direct.builder("after")
                .runnable(context -> {
                    afterTestCount.incrementAndGet();
                    assertThat(context.getStore().get("test-before-ran", Boolean.class))
                            .isPresent();
                })
                .build();
    }

    private static Action argumentAfter() {
        return Direct.builder("after")
                .runnable(context -> {
                    afterArgumentCount.incrementAndGet();
                    assertThat(context.getStore().remove("argument", TestState.class))
                            .isPresent();
                })
                .build();
    }

    private static Action suiteAfter() {
        return Direct.builder("after")
                .runnable(context -> {
                    afterCount.incrementAndGet();
                    assertThat(context.getStore().get("suite", TestState.class)).isPresent();
                    assertThat(context.getAncestor("/")).isNotNull();
                    assertThat(beforeCount.get()).isEqualTo(1);
                    assertThat(beforeArgumentCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
                    assertThat(beforeTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(invokeTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(afterTestCount.get())
                            .isEqualTo(EXPECTED_ARGUMENT_COUNT * EXPECTED_METHOD_COUNT_PER_ARGUMENT);
                    assertThat(afterArgumentCount.get()).isEqualTo(EXPECTED_ARGUMENT_COUNT);
                    assertThat(afterCount.get()).isEqualTo(1);
                })
                .build();
    }

    private static void resetCounts() {
        beforeCount.set(0);
        beforeArgumentCount.set(0);
        beforeTestCount.set(0);
        invokeTestCount.set(0);
        afterTestCount.set(0);
        afterArgumentCount.set(0);
        afterCount.set(0);
    }
}
