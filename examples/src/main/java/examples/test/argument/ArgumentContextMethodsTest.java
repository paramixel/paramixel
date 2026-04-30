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

package examples.test.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.paramixel.core.Action;
import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Sequential;

public class ArgumentContextMethodsTest {

    record TestAttachment(String argumentName) {}

    public static void main(String[] args) {
        ConsoleRunner.runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        return Sequential.of(
                "ArgumentContextMethodsTest",
                List.of(argumentAction("arg-0"), argumentAction("arg-1"), argumentAction("arg-2")));
    }

    private static Action argumentAction(String argumentName) {
        Action body = Direct.of("assert-context", context -> {
            assertThat(context.getParent()).isPresent();
        });

        return Lifecycle.of(
                argumentName,
                Direct.of("before", context -> context.setAttachment(new TestAttachment(argumentName))),
                Sequential.of(argumentName + "-body", List.of(body)),
                Noop.of("after"));
    }
}
