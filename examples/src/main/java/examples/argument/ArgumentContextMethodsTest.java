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

import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;

public class ArgumentContextMethodsTest {

    record TestStoreValue(String argumentName) {}

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        var suiteBuilder = Container.builder("ArgumentContextMethodsTest")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        for (String argumentName : new String[] {"arg-0", "arg-1", "arg-2"}) {
            Action argument = argument(argumentName);
            suiteBuilder.child(argument);
        }
        return suiteBuilder.build();
    }

    private static Action argument(String argumentName) {
        Action before = before(argumentName);
        Action body = body(argumentName);
        Action after = after();

        return Container.builder(argumentName)
                .before(before)
                .child(body)
                .after(after)
                .build();
    }

    private static Action before(String argumentName) {
        return Direct.builder("before")
                .runnable(context -> context.getStore().put("argument", new TestStoreValue(argumentName)))
                .build();
    }

    private static Action body(String argumentName) {
        Action assertContext = assertContext(argumentName);

        var bodyBuilder = Container.builder(argumentName + "-body")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        bodyBuilder.child(assertContext);
        return bodyBuilder.build();
    }

    private static Action assertContext(String argumentName) {
        return Direct.builder("assert-context")
                .runnable(context -> {
                    assertThat(context.getParent()).isNotNull();
                    assertThat(context.getAncestor("../../")
                                    .getStore()
                                    .get("argument", TestStoreValue.class)
                                    .orElseThrow()
                                    .argumentName())
                            .isEqualTo(argumentName);
                })
                .build();
    }

    private static Action after() {
        return Noop.of("after");
    }
}
