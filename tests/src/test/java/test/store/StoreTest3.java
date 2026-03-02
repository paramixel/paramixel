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

package test.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies store access using enum keys with an instance method for key scoping.
 */
public class StoreTest3 {

    /**
     * Keys used to create scoped store key names.
     */
    enum Key {

        /** Key used in the engine store. */
        ENGINE_CONTEXT_KEY,

        /** Key used in the class store. */
        CLASS_CONTEXT_KEY,

        /** Key used in the argument store. */
        ARGUMENT_CONTEXT_KEY;

        /**
         * Creates a globally-scoped key name for this enum constant.
         *
         * @return scoped key name
         */
        public String scopedName() {
            return getClass().getName() + "." + name();
        }
    }

    /**
     * Supplies a single argument to drive store assertions.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArgument("test");
    }

    /**
     * Populates engine and class stores prior to test execution.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        String payload = context.getArgument(String.class);
        System.out.printf("beforeAll(%s)%n", payload);

        context.getClassContext().getEngineContext().getStore().put(Key.ENGINE_CONTEXT_KEY.scopedName(), "engine");
        context.getClassContext().getStore().put(Key.CLASS_CONTEXT_KEY.scopedName(), "class");
        context.getClassContext().getStore().put(argumentKey(context), "argument");
    }

    /**
     * Verifies store values are readable from the expected scopes.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void verifyMaps(final @NonNull ArgumentContext context) {
        String payload = context.getArgument(String.class);
        System.out.printf("verifyMaps(%s)%n", payload);

        Object engineValue =
                context.getClassContext().getEngineContext().getStore().get(Key.ENGINE_CONTEXT_KEY.scopedName());
        Object classValue = context.getClassContext().getStore().get(Key.CLASS_CONTEXT_KEY.scopedName());
        Object argumentValue = context.getClassContext().getStore().get(argumentKey(context));

        assertThat(engineValue).isEqualTo("engine");
        assertThat(classValue).isEqualTo("class");
        assertThat(argumentValue).isEqualTo("argument");

        context.getStore().put(Key.ARGUMENT_CONTEXT_KEY.scopedName(), "argument");
        assertThat(context.getStore().get(Key.ARGUMENT_CONTEXT_KEY.scopedName()))
                .isEqualTo("argument");
    }

    /**
     * Removes the per-argument key from the class store.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        Object removed = context.getClassContext().getStore().remove(argumentKey(context));
        assertThat(removed).isEqualTo("argument");
    }

    /**
     * Removes engine and class keys once the class finishes executing.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        Object engineRemoved = context.getEngineContext().getStore().remove(Key.ENGINE_CONTEXT_KEY.scopedName());
        Object classRemoved = context.getStore().remove(Key.CLASS_CONTEXT_KEY.scopedName());

        assertThat(engineRemoved).isEqualTo("engine");
        assertThat(classRemoved).isEqualTo("class");
    }

    /**
     * Builds a unique per-argument key name.
     *
     * @param context for the current argument
     * @return a key including the argument index
     */
    private static String argumentKey(final @NonNull ArgumentContext context) {
        return Key.ARGUMENT_CONTEXT_KEY.scopedName() + "." + context.getArgumentIndex();
    }
}
