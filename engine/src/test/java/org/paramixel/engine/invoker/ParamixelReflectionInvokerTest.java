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

package org.paramixel.engine.invoker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.api.ConcreteEngineContext;

public class ParamixelReflectionInvokerTest {

    @Test
    public void invokesPrivateLifecycleMethodsByMakingAccessible() throws Throwable {
        final Target target = new Target();
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(Target.class, engineContext, target);
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext("arg", 0);

        final Method initialize = Target.class.getDeclaredMethod("initialize", ClassContext.class);
        final Method beforeEach = Target.class.getDeclaredMethod("beforeEach", ArgumentContext.class);

        ParamixelReflectionInvoker.invokeInitialize(initialize, target, classContext);
        ParamixelReflectionInvoker.invokeBeforeEach(beforeEach, target, argumentContext);

        // hit accessible cache on second call
        ParamixelReflectionInvoker.invokeInitialize(initialize, target, classContext);

        assertThat(target.initializeCalls).isEqualTo(2);
        assertThat(target.beforeEachCalls).isEqualTo(1);
    }

    @Test
    public void unwrapsInvocationTargetExceptionCause() throws Exception {
        final ThrowingTarget target = new ThrowingTarget();
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext = new ConcreteClassContext(ThrowingTarget.class, engineContext, target);
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(null, 0);
        final Method testMethod = ThrowingTarget.class.getDeclaredMethod("test", ArgumentContext.class);

        assertThatThrownBy(() -> ParamixelReflectionInvoker.invokeTestMethod(testMethod, target, argumentContext))
                .isInstanceOf(Boom.class)
                .hasMessage("boom");
    }

    @Test
    public void wrapsIllegalAccessException_whenCacheClaimsAccessibleButMethodIsNot() throws Exception {
        final InaccessibleTarget target = new InaccessibleTarget();
        final ConcreteEngineContext engineContext = new ConcreteEngineContext("paramixel", new Properties(), 1);
        final ConcreteClassContext classContext =
                new ConcreteClassContext(InaccessibleTarget.class, engineContext, target);
        final ArgumentContext argumentContext = classContext.getOrCreateArgumentContext(null, 0);

        final Method beforeEach = InaccessibleTarget.class.getDeclaredMethod("beforeEach", ArgumentContext.class);

        final Field cacheField = ParamixelReflectionInvoker.class.getDeclaredField("ACCESSIBLE_CACHE");
        cacheField.setAccessible(true);

        final Object rawCache = cacheField.get(null);
        rawCache.getClass().getMethod("put", Object.class, Object.class).invoke(rawCache, beforeEach, Boolean.TRUE);

        assertThatThrownBy(() -> ParamixelReflectionInvoker.invokeBeforeEach(beforeEach, target, argumentContext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to invoke method");
    }

    @Test
    public void invokeStatic_invokesPrivateStaticMethod_andReturnsValue() throws Throwable {
        final Method supplier = StaticTarget.class.getDeclaredMethod("supplier");
        assertThat(ParamixelReflectionInvoker.invokeStatic(supplier)).isEqualTo("value");
    }

    @Test
    public void invokeStatic_unwrapsInvocationTargetExceptionCause() throws Exception {
        final Method m = StaticTarget.class.getDeclaredMethod("throwsBoom");
        assertThatThrownBy(() -> ParamixelReflectionInvoker.invokeStatic(m))
                .isInstanceOf(Boom.class)
                .hasMessage("boom");
    }

    private static final class Target {

        private int initializeCalls;
        private int beforeEachCalls;

        private void initialize(final ClassContext context) {
            initializeCalls++;
        }

        private void beforeEach(final ArgumentContext context) {
            beforeEachCalls++;
        }
    }

    private static final class ThrowingTarget {

        private void test(final ArgumentContext context) {
            throw new Boom("boom");
        }
    }

    private static final class InaccessibleTarget {

        private void beforeEach(final ArgumentContext context) {
            // INTENTIONALLY EMPTY
        }
    }

    private static final class Boom extends RuntimeException {

        private Boom(final String message) {
            super(message);
        }
    }

    private static final class StaticTarget {

        private static String supplier() {
            return "value";
        }

        private static void throwsBoom() {
            throw new Boom("boom");
        }
    }
}
