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

package test.annotation;

import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Executable;
import org.paramixel.core.action.Lifecycle;
import test.util.RunnerSupport;

public class DisabledTest {

    private static final Executable FAILING_ACTION = context -> {
        throw new AssertionError("Disabled action must not execute");
    };

    @Paramixel.Disabled("covered by resolver skip behavior")
    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Lifecycle.of(
                "DisabledTest", (Executable) null, Direct.of("disabled-leaf", FAILING_ACTION), FAILING_ACTION);
    }

    public static void main(String[] args) {
        RunnerSupport.runPackage(DisabledTest.class);
    }
}
