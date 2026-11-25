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

package nonapi.org.paramixel.authoring;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.paramixel.api.exception.ParamixelException;

/**
 * Invokes a resolved hook method.
 *
 * <p>The invoker exists for one correctness reason: {@link Method#invoke} wraps whatever the hook throws in an
 * {@link InvocationTargetException}. Classifying that wrapper instead of its cause would turn a {@code FailException},
 * {@code SkipException} or {@code AbortException} into an infrastructure error and change the reported status of a
 * test, so the cause is always unwrapped before it reaches the engine.
 */
public final class HookInvoker {

    private HookInvoker() {
        // Intentionally empty
    }

    /**
     * Invokes a static hook method and returns its value.
     *
     * @param method the resolved method; must be {@code static}
     * @param arguments the arguments
     * @return the method's return value, or {@code null} for {@code void}
     * @throws Throwable the cause the hook threw, unwrapped
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    public static Object call(final Method method, final Object... arguments) throws Throwable {
        try {
            return method.invoke(null, arguments);
        } catch (final InvocationTargetException e) {
            throw unwrap(e);
        } catch (final IllegalAccessException e) {
            // A hook declared in a non-public class: resolve on the classpath first, then open it.
            method.setAccessible(true);
            try {
                return method.invoke(null, arguments);
            } catch (final InvocationTargetException inner) {
                throw unwrap(inner);
            } catch (final IllegalAccessException inner) {
                throw new ParamixelException("Cannot invoke hook method " + method + ": " + inner.getMessage(), inner);
            }
        }
    }

    private static Throwable unwrap(final InvocationTargetException e) {
        final var cause = e.getCause();
        return cause == null ? e : cause;
    }
}
