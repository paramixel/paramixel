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

package examples.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.paramixel.core.Action;
import org.paramixel.core.Action.ContextMode;
import org.paramixel.core.Context;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Value;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class InheritedContextTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action before = before();
        Action arguments = arguments();
        Action after = after();

        return Container.builder("InheritedContextTest")
                .before(before)
                .child(arguments)
                .after(after)
                .build();
    }

    private static Action before() {
        return Direct.builder("before")
                .contextMode(ContextMode.SHARED)
                .execute(context -> context.getStore().put("suite-key", Value.of("suite-value")))
                .build();
    }

    private static Action arguments() {
        var builder = Container.builder("arguments")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        for (String name : new String[] {"arg-0", "arg-1"}) {
            builder.child(argument(name));
        }
        return builder.build();
    }

    private static Action argument(String name) {
        Action before = argumentBefore(name);
        Action body = argumentBody(name);

        return Container.builder(name).before(before).child(body).build();
    }

    private static Action argumentBefore(String name) {
        return Direct.builder("before")
                .contextMode(ContextMode.SHARED)
                .execute(context -> context.getStore().put("arg-key", Value.of(name)))
                .build();
    }

    private static Action argumentBody(String name) {
        return Container.builder(name + "-body").child(verifyIsolated(name)).build();
    }

    private static Action verifyIsolated(String name) {
        return Direct.builder("verify-isolated")
                .execute(context -> {
                    assertThat(context.getStore().get("arg-key")).isEmpty();

                    Context parent = context.getParent().orElseThrow();
                    assertThat(parent.getStore().get("arg-key")).isEmpty();

                    Context grandparent = parent.getParent().orElseThrow();
                    assertThat(grandparent
                                    .getStore()
                                    .get("arg-key")
                                    .orElseThrow()
                                    .cast(String.class))
                            .isEqualTo(name);

                    Context greatGrandparent = grandparent.getParent().orElseThrow();
                    Context greatGreatGrandparent = greatGrandparent.getParent().orElseThrow();
                    assertThat(greatGreatGrandparent
                                    .getStore()
                                    .get("suite-key")
                                    .orElseThrow()
                                    .cast(String.class))
                            .isEqualTo("suite-value");
                })
                .build();
    }

    private static Action after() {
        return Direct.builder("after")
                .contextMode(ContextMode.SHARED)
                .execute(context -> assertThat(context.getStore()
                                .get("suite-key")
                                .orElseThrow()
                                .cast(String.class))
                        .isEqualTo("suite-value"))
                .build();
    }
}
