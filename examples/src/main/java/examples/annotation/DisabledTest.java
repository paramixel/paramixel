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

package examples.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class DisabledTest {

    private static final AtomicBoolean factoryInvoked = new AtomicBoolean();

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(disabledVerificationFactory());
    }

    @Paramixel.ActionFactory
    public static Action disabledVerificationFactory() {
        return Direct.builder("DisabledVerification")
                .runnable(context -> assertThat(factoryInvoked.get())
                        .as("@Paramixel.Disabled must prevent the action factory from being invoked")
                        .isFalse())
                .build();
    }

    @Paramixel.Disabled("covered by resolver skip behavior")
    @Paramixel.ActionFactory
    public static Action actionFactory() {
        factoryInvoked.set(true);

        Action before = Direct.builder("before")
                .runnable(context -> {
                    throw new AssertionError("Disabled action must not execute");
                })
                .build();

        Action child = Direct.builder("disabled-leaf")
                .runnable(context -> {
                    throw new AssertionError("Disabled action must not execute");
                })
                .build();

        Action after = Direct.builder("after")
                .runnable(context -> {
                    throw new AssertionError("Disabled action must not execute");
                })
                .build();

        return Container.builder("DisabledTest")
                .before(before)
                .child(child)
                .after(after)
                .build();
    }
}
