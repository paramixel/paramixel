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

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

public class ParamixelTestMethodDescriptorEngineExecutionListenerTest {

    @Test
    public void printsStartAndFinishLines() throws Exception {
        final StringBuilder out = new StringBuilder();
        final Consumer<String> printer = line -> out.append(line).append("\n");

        final UniqueId root = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(root.append("class", "c"), String.class, "C");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");
        final Method m = ParamixelTestMethodDescriptorEngineExecutionListenerTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        clazz.addChild(arg);
        arg.addChild(method);

        final ParamixelTestMethodDescriptorEngineExecutionListener listener =
                new ParamixelTestMethodDescriptorEngineExecutionListener(printer);

        listener.executionStarted(method);
        listener.executionFinished(method, TestExecutionResult.aborted(new RuntimeException("abort")));

        assertThat(out.toString()).contains("M").contains("ABORTED");
    }

    private static void dummy() {
        // INTENTIONALLY EMPTY
    }
}
