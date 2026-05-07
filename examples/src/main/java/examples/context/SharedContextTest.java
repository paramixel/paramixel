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
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Value;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class SharedContextTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action before = before();
        Action writeChild = writeChild();
        Action readChild = readChild();
        Action after = after();

        return Container.builder("SharedContextTest")
                .before(before)
                .child(writeChild)
                .child(readChild)
                .after(after)
                .build();
    }

    private static Action before() {
        return Direct.builder("before")
                .contextMode(ContextMode.SHARED)
                .execute(context -> context.getStore().put("shared-key", Value.of("shared-value")))
                .build();
    }

    private static Action writeChild() {
        return Direct.builder("write-child")
                .contextMode(ContextMode.SHARED)
                .execute(context -> context.getStore().put("child-key", Value.of("child-value")))
                .build();
    }

    private static Action readChild() {
        return Direct.builder("read-child")
                .contextMode(ContextMode.SHARED)
                .execute(context -> {
                    assertThat(context.getStore()
                                    .get("shared-key")
                                    .orElseThrow()
                                    .cast(String.class))
                            .isEqualTo("shared-value");
                    assertThat(context.getStore().get("child-key").orElseThrow().cast(String.class))
                            .isEqualTo("child-value");
                })
                .build();
    }

    private static Action after() {
        return Direct.builder("after")
                .contextMode(ContextMode.SHARED)
                .execute(context -> {
                    assertThat(context.getStore()
                                    .get("shared-key")
                                    .orElseThrow()
                                    .cast(String.class))
                            .isEqualTo("shared-value");
                    assertThat(context.getStore().get("child-key").orElseThrow().cast(String.class))
                            .isEqualTo("child-value");
                })
                .build();
    }
}
