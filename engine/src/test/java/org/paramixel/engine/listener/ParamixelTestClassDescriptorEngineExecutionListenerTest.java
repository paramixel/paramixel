/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;

public class ParamixelTestClassDescriptorEngineExecutionListenerTest {

    @Test
    public void printsStartAndFinishLines() {
        final StringBuilder out = new StringBuilder();
        final Consumer<String> printer = line -> out.append(line).append("\n");

        final ParamixelTestClassDescriptor clazz = new ParamixelTestClassDescriptor(
                UniqueId.forEngine("paramixel").append("class", "c"), String.class, "C");

        final ParamixelTestClassDescriptorEngineExecutionListener listener =
                new ParamixelTestClassDescriptorEngineExecutionListener(printer);

        listener.executionStarted(clazz);
        listener.executionFinished(clazz, TestExecutionResult.successful());

        assertThat(out.toString()).contains("C").contains("PASS");
    }
}
