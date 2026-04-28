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

package test.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.paramixel.core.Action;
import org.paramixel.core.Paramixel;
import org.paramixel.core.action.Direct;
import org.paramixel.core.action.Executable;
import org.paramixel.core.action.Lifecycle;
import org.paramixel.core.action.Sequential;
import test.util.RunnerSupport;

public class ContextHierarchyTest {

    record Attachment(String value) {}

    @Paramixel.ActionFactory
    public static Action actionFactory() {
        Action arguments = Sequential.of("arguments", List.of(argumentAction("arg-0"), argumentAction("arg-1")));
        return Lifecycle.of(
                "ContextHierarchyTest",
                context -> context.setAttachment(new Attachment("suite-value")),
                arguments,
                (Executable) null);
    }

    private static Action argumentAction(String name) {
        Action inspectParent = Direct.of("inspect-parent", context -> {
            assertThat(context.parent()).isPresent();
            assertThat(context.parent().orElseThrow().action().name()).isEqualTo(name + "-body");
            assertThat(context.parent().orElseThrow().parent()).isPresent();
            assertThat(context.action().parent()).isPresent();
            assertThat(context.action().parent().orElseThrow().name()).isEqualTo(name + "-body");
        });

        Action inspectStores = Direct.of("inspect-stores", context -> {
            var argumentContext = context.parent().orElseThrow().parent().orElseThrow();
            var suiteContext = argumentContext.parent().orElseThrow().parent().orElseThrow();
            assertThat(argumentContext
                            .attachment(Attachment.class)
                            .orElseThrow()
                            .value())
                    .isEqualTo(name);
            assertThat(suiteContext.attachment(Attachment.class).orElseThrow().value())
                    .isEqualTo("suite-value");
        });

        return Lifecycle.of(
                name,
                context -> context.setAttachment(new Attachment(name)),
                Sequential.of(name + "-body", List.of(inspectParent, inspectStores)),
                (Executable) null);
    }

    public static void main(String[] args) {
        RunnerSupport.runAction(actionFactory());
    }
}
