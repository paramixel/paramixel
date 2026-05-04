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
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.Value;
import org.paramixel.core.action.Direct;

public class StoreOperationsTest {

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Direct.of("store-operations", context -> {
            context.getStore().put("counter", Value.of(1));
            context.getStore().compute("counter", (key, value) -> Value.of(value.cast(Integer.class) + 1));
            context.getStore().putIfAbsent("message", Value.of("hello"));

            assertThat(context.getStore().get("counter").orElseThrow().cast(Integer.class))
                    .isEqualTo(2);
            assertThat(context.getStore().get("message").orElseThrow().cast(String.class))
                    .isEqualTo("hello");
        });
    }
}
