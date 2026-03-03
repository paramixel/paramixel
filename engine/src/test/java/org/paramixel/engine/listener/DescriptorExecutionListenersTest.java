/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

public class DescriptorExecutionListenersTest {

    @Test
    public void descriptorSpecificListeners_printStartAndEndLines() throws Exception {
        final StringBuilder out = new StringBuilder();
        final var printer =
                (java.util.function.Consumer<String>) line -> out.append(line).append("\n");

        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");
        final var m = DescriptorExecutionListenersTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        clazz.addChild(arg);
        arg.addChild(method);

        final var classListener = new ParamixelTestClassDescriptorEngineExecutionListener(printer);
        classListener.executionStarted(clazz);
        classListener.executionFinished(clazz, TestExecutionResult.successful());

        final var argListener = new ParamixelTestArgumentDescriptorEngineExecutionListener(printer);
        argListener.executionStarted(arg);
        argListener.executionFinished(arg, TestExecutionResult.failed(new RuntimeException("boom")));

        final var methodListener = new ParamixelTestMethodDescriptorEngineExecutionListener(printer);
        methodListener.executionStarted(method);
        methodListener.executionFinished(method, TestExecutionResult.aborted(new RuntimeException("abort")));

        final String output = out.toString();
        assertThat(output).contains("C");
        assertThat(output).contains("A0");
        assertThat(output).contains("M");
        assertThat(output).contains("PASS");
        assertThat(output).contains("FAIL");
        assertThat(output).contains("ABORTED");
    }

    private static void dummy() {}
}
