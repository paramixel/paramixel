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

package org.paramixel.engine.descriptor;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;

class ParamixelDescriptorTypesTest {

    @Test
    public void classAndArgumentAndMethodDescriptors_exposeExpectedState() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");

        assertThat(clazz.getType()).isEqualTo(TestDescriptor.Type.CONTAINER);
        assertThat(clazz.getTestClass()).isEqualTo(String.class);

        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");

        assertThat(arg.getType()).isEqualTo(TestDescriptor.Type.CONTAINER);
        assertThat(arg.getArgumentIndex()).isZero();
        assertThat(arg.getArgument()).isNull();
        assertThat(arg.toString()).contains("uniqueId=").contains("argumentIndex=0");

        final Method method = ParamixelDescriptorTypesTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor m =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), method, "M");

        assertThat(m.getType()).isEqualTo(TestDescriptor.Type.TEST);
        assertThat(m.getTestMethod()).isSameAs(method);
        assertThat(m.getChildren()).isEmpty();
    }

    @Test
    public void invocationDescriptor_hasExpectedDisplayNameAndNoChildren() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelInvocationDescriptor invocation =
                new ParamixelInvocationDescriptor(rootId.append("invocation", "7"), 7, "arg");

        assertThat(invocation.getDisplayName()).isEqualTo("invocation:7");
        assertThat(invocation.getArgument()).isEqualTo("arg");
        assertThat(invocation.getType()).isEqualTo(TestDescriptor.Type.TEST);
        assertThat(invocation.getChildren()).isEmpty();
        assertThat(invocation.toString()).contains("invocationIndex=7").contains("uniqueId=");
    }

    private static void dummy() {}
}
