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

import org.paramixel.core.Action;
import org.paramixel.core.Factory;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Container;
import org.paramixel.core.action.Direct;

public class DisabledTest {

    private static final Runnable FAILING_ACTION = () -> {
        throw new AssertionError("Disabled action must not execute");
    };

    public static void main(String[] args) {
        Factory.defaultRunner().runAndExit(actionFactory());
    }

    @Paramixel.Disabled("covered by resolver skip behavior")
    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action before = Direct.builder("before")
                .execute(context -> FAILING_ACTION.run())
                .build();

        Action child = Direct.builder("disabled-leaf")
                .execute(context -> FAILING_ACTION.run())
                .build();

        Action after =
                Direct.builder("after").execute(context -> FAILING_ACTION.run()).build();

        return Container.builder("DisabledTest")
                .before(before)
                .child(child)
                .after(after)
                .build();
    }
}
