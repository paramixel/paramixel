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

package test.util;

import java.util.Optional;
import org.paramixel.core.Action;
import org.paramixel.core.Resolver;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Selector;

public final class RunnerSupport {

    private RunnerSupport() {}

    public static void runPackage(Class<?> clazz) {
        Optional<Action> optionalAction = Resolver.resolveActions(Selector.byPackageName(clazz));

        if (optionalAction.isEmpty()) {
            System.err.println("No actions found for package: " + clazz.getPackageName());
            System.exit(1);
        }

        Action action = optionalAction.get();
        Result result = Runner.builder().build().run(action);

        if (result.status() == Result.Status.FAIL) {
            System.err.println("Execution failed");
            System.exit(1);
        }

        System.out.println("Execution passed");
        System.exit(0);
    }

    public static void runAction(Action action) {
        Result result = Runner.builder().build().run(action);
        if (result.status() == Result.Status.FAIL) {
            System.exit(1);
        }
    }
}
