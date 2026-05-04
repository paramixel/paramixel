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

import java.util.List;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Value;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Sequential;

public class StoreContextTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Lifecycle.of(
                "store-example",
                Direct.of("before", context -> context.getStore().put("suite", Value.of("suite-value"))),
                Sequential.of(
                        "body",
                        List.of(
                                Direct.of(
                                        "write-child",
                                        context -> context.getParent()
                                                .orElseThrow()
                                                .getStore()
                                                .put("child", Value.of("child-value"))),
                                Direct.of("read-values", context -> {
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
                                }))),
                Noop.of("after"));
    }
}
