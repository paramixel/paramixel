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

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;

class AbstractEngineExecutionListenerTest {

    @Test
    public void getDisplayName_buildsFromHierarchyUpToDepth() throws Exception {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "C");
        final ParamixelTestArgumentDescriptor arg =
                new ParamixelTestArgumentDescriptor(clazz.getUniqueId().append("argument", "0"), 0, null, "A0");
        final var m = AbstractEngineExecutionListenerTest.class.getDeclaredMethod("dummy");
        final ParamixelTestMethodDescriptor method =
                new ParamixelTestMethodDescriptor(arg.getUniqueId().append("method", "m"), m, "M");
        clazz.addChild(arg);
        arg.addChild(method);

        final TestableListener listener = new TestableListener();
        assertThat(listener.displayName(1, method)).isEqualTo("M");
        assertThat(listener.displayName(2, method)).isEqualTo("A0 | M");
        assertThat(listener.displayName(3, method)).isEqualTo("C | A0 | M");
    }

    @Test
    public void getStatusMessage_includesThreadAndDisplayName_andVariesByStatus() {
        final TestableListener listener = new TestableListener();
        assertThat(listener.statusMessage(TestExecutionResult.successful(), "t", "d"))
                .contains("t")
                .contains("d")
                .contains("PASS");

        assertThat(listener.statusMessage(TestExecutionResult.failed(new RuntimeException("boom")), "t", "d"))
                .contains("t")
                .contains("d")
                .contains("FAIL");

        assertThat(listener.statusMessage(TestExecutionResult.aborted(new RuntimeException("abort")), "t", "d"))
                .contains("t")
                .contains("d")
                .contains("ABORTED");
    }

    private static void dummy() {}

    private static final class TestableListener extends AbstractEngineExecutionListener {
        String displayName(final int depth, final org.junit.platform.engine.TestDescriptor descriptor) {
            return getDisplayName(depth, descriptor);
        }

        String statusMessage(final TestExecutionResult result, final String thread, final String displayName) {
            return getStatusMessage(result, thread, displayName);
        }
    }
}
