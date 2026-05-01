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

import java.util.List;
import org.paramixel.core.Action;
import org.paramixel.core.ConsoleRunner;
import org.paramixel.core.Context;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Noop;
import org.paramixel.core.action.Sequential;

public class ContextHierarchyTest {

    record TestAttachment(String value) {}

    public static void main(String[] args) {
        ConsoleRunner.runAndExit(actionFactory());
    }

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action arguments = Sequential.of("arguments", List.of(argumentAction("arg-0"), argumentAction("arg-1")));
        return Lifecycle.of(
                "ContextHierarchyTest",
                Direct.of("before", context -> context.setAttachment(new TestAttachment("suite-value"))),
                arguments,
                Noop.of("after"));
    }

    private static Action argumentAction(String name) {
        Action inspectParent = Direct.of("inspect-parent", context -> {
            assertThat(context.getParent()).isPresent();
            Context current = context;
            while (current.getParent().isPresent()) {
                current = current.getParent().orElseThrow();
            }
            assertThat(current.getParent()).isEmpty();
        });

        Action inspectStores = Direct.of("inspect-stores", context -> {
            var argumentContext = context.findContext(2).orElseThrow();
            assertThat(argumentContext
                            .getAttachment()
                            .flatMap(a -> a.to(TestAttachment.class))
                            .orElseThrow()
                            .value())
                    .isEqualTo(name);

            var suiteContext = context.findContext(5).orElseThrow();
            assertThat(suiteContext
                            .getAttachment()
                            .flatMap(a -> a.to(TestAttachment.class))
                            .orElseThrow()
                            .value())
                    .isEqualTo("suite-value");
        });

        return Lifecycle.of(
                name,
                Direct.of("before", context -> context.setAttachment(new TestAttachment(name))),
                Sequential.of(name + "-body", List.of(inspectParent, inspectStores)),
                Noop.of("after"));
    }
}
