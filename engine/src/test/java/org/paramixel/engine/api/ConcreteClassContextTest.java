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

package org.paramixel.engine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class ConcreteClassContextTest {

    @Test
    public void getters_returnConstructorValues_andStoreIsNonNull() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final Object instance = new Object();
        final ConcreteClassContext classContext = new ConcreteClassContext(String.class, engineContext, instance);

        assertThat(classContext.getTestClass()).isEqualTo(String.class);
        assertThat(classContext.getTestClassName()).isEqualTo(String.class.getName());
        assertThat(classContext.getEngineContext()).isSameAs(engineContext);
        assertThat(classContext.getTestInstance()).isSameAs(instance);
        assertThat(classContext.getStore()).isNotNull();
    }

    @Test
    public void argumentContexts_areCachedByIndex_andRemovable() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(String.class, engineContext, null);

        final ConcreteArgumentContext a0 = classContext.getOrCreateArgumentContext("a", 0);
        final ConcreteArgumentContext a0Again = classContext.getOrCreateArgumentContext("b", 0);
        final ConcreteArgumentContext a1 = classContext.getOrCreateArgumentContext("x", 1);

        assertThat(a0Again).isSameAs(a0);
        assertThat(a1).isNotSameAs(a0);

        assertThat(classContext.removeArgumentContext(0)).isSameAs(a0);
        assertThat(classContext.removeArgumentContext(0)).isNull();
        assertThat(classContext.removeArgumentContext(1)).isSameAs(a1);
    }

    @Test
    public void recordFailure_keepsFirstFailureOnly() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(String.class, engineContext, null);

        final RuntimeException first = new RuntimeException("first");
        final RuntimeException second = new RuntimeException("second");

        classContext.recordFailure(first);
        classContext.recordFailure(second);

        assertThat(classContext.getFirstFailure()).isSameAs(first);
    }

    @Test
    public void recordFailure_throwsOnNullFailure() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(String.class, engineContext, null);
        assertThatThrownBy(() -> classContext.recordFailure(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void incrementCounters_areReflectedInToString() {
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(String.class, engineContext, null);

        classContext.incrementInvocationCount();
        classContext.incrementInvocationCount();
        classContext.incrementSuccessCount();
        classContext.incrementFailureCount();

        assertThat(classContext.toString()).contains("invocationCount=2");
        assertThat(classContext.toString()).contains("successCount=1");
        assertThat(classContext.toString()).contains("failureCount=1");
    }
}
