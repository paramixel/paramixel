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

package org.paramixel.engine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class ConcreteArgumentContextTest {

    @Test
    public void constructor_rejectsNullClassContext() {
        assertThatThrownBy(() -> new ConcreteArgumentContext(null, "x", 0)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void getters_returnExpectedValues_andStoreIsNonNull() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(String.class, engineContext, null);
        final ConcreteArgumentContext argumentContext = new ConcreteArgumentContext(classContext, "arg", 3);

        assertThat(argumentContext.getClassContext()).isSameAs(classContext);
        assertThat(argumentContext.getArgument()).isEqualTo("arg");
        assertThat(argumentContext.getArgumentIndex()).isEqualTo(3);
        assertThat(argumentContext.getStore()).isNotNull();
    }

    @Test
    public void equalsAndHashCode_considerClassContextArgumentAndIndex() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(String.class, engineContext, null);

        final ConcreteArgumentContext a1 = new ConcreteArgumentContext(classContext, "x", 0);
        final ConcreteArgumentContext a2 = new ConcreteArgumentContext(classContext, "x", 0);
        final ConcreteArgumentContext b = new ConcreteArgumentContext(classContext, "x", 1);

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
        assertThat(a1).isNotEqualTo(b);
        assertThat(a1).isNotEqualTo(null);
        assertThat(a1).isNotEqualTo("not-a-context");
        assertThat(a1).isEqualTo(a1);
    }

    @Test
    public void toString_includesClassNameAndIndex() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(String.class, engineContext, null);
        final ConcreteArgumentContext argumentContext = new ConcreteArgumentContext(classContext, "arg", 7);

        assertThat(argumentContext.toString()).contains("testClass=");
        assertThat(argumentContext.toString()).contains("invocationIndex=7");
    }
}
