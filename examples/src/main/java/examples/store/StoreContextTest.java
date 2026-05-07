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

package examples.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.paramixel.core.Action;
import org.paramixel.core.Action.ContextMode;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Value;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Noop;

public class StoreContextTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action before = before();
        Action body = body();
        Action after = after();

        return Container.builder("store-example")
                .before(before)
                .child(body)
                .after(after)
                .build();
    }

    private static Action before() {
        return Direct.builder("before")
                .contextMode(ContextMode.SHARED)
                .execute(context -> context.getStore().put("suite", Value.of("suite-value")))
                .build();
    }

    private static Action body() {
        Action writeChild = writeChild();
        Action readValues = readValues();

        var bodyBuilder = Container.builder("body")
                .policy(Container.Policy.builder()
                        .childMode(Container.ChildMode.INDEPENDENT)
                        .build());
        bodyBuilder.child(writeChild);
        bodyBuilder.child(readValues);
        return bodyBuilder.build();
    }

    private static Action writeChild() {
        return Direct.builder("write-child")
                .execute(context -> context.getParent().orElseThrow().getStore().put("child", Value.of("child-value")))
                .build();
    }

    private static Action readValues() {
        return Direct.builder("read-values")
                .execute(context -> {
                    String child = context.findAncestor(1)
                            .orElseThrow()
                            .getStore()
                            .get("child")
                            .orElseThrow()
                            .cast(String.class);
                    String suite = context.findAncestor(2)
                            .orElseThrow()
                            .getStore()
                            .get("suite")
                            .orElseThrow()
                            .cast(String.class);

                    assertThat(child).isEqualTo("child-value");
                    assertThat(suite).isEqualTo("suite-value");
                })
                .build();
    }

    private static Action after() {
        return Noop.of("after");
    }
}
