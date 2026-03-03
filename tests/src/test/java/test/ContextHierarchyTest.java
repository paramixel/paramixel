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

package test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.EngineContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies the context hierarchy: ArgumentContext -> ClassContext -> EngineContext.
 *
 * <p>This test validates that contexts are properly linked and accessible from
 * each level of the hierarchy.
 */
public class ContextHierarchyTest {

    private static final AtomicInteger testCount = new AtomicInteger(0);

    @Paramixel.Test
    public void testContextHierarchy(final @NonNull ArgumentContext argumentContext) {
        // ArgumentContext should provide access to ClassContext
        ClassContext classContext = argumentContext.getClassContext();
        assertThat(classContext).isNotNull();

        // ClassContext should provide access to EngineContext
        EngineContext engineContext = classContext.getEngineContext();
        assertThat(engineContext).isNotNull();

        // Verify all stores are non-null and distinct
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(classContext.getStore()).isNotNull();
        assertThat(engineContext.getStore()).isNotNull();

        // Stores should be different instances (different scopes)
        assertThat(argumentContext.getStore()).isNotSameAs(classContext.getStore());
        assertThat(classContext.getStore()).isNotSameAs(engineContext.getStore());

        // Verify ClassContext properties
        assertThat(classContext.getTestClass()).isEqualTo(ContextHierarchyTest.class);
        assertThat(classContext.getTestInstance()).isNotNull();

        // Verify EngineContext properties
        assertThat(engineContext.getEngineId()).isEqualTo("paramixel");

        testCount.incrementAndGet();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        // Without ArgumentsCollector, test runs once with null argument
        assertThat(testCount.get()).isEqualTo(1);
    }
}
