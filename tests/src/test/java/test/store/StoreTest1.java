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
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Store;

@Paramixel.TestClass
/**
 * Verifies store scoping and lifecycle visibility.
 *
 * <p>This test writes values into the engine, class, and argument stores during lifecycle hooks
 * and asserts that the values are visible where expected and removable at the appropriate time.
 */
public class StoreTest1 {

    /** Prefix used to create unique keys within stores. */
    private static final String PREFIX = StoreTest1.class.getName();

    /** Key used in the per-argument {@link Store}. */
    private static final String ARGUMENT_CONTEXT_KEY = PREFIX + "argument.context.key";

    /** Key used in the per-class {@link Store}. */
    private static final String CLASS_CONTEXT_KEY = PREFIX + "class.context.key";

    /** Key used in the engine {@link Store}. */
    private static final String ENGINE_CONTEXT_KEY = PREFIX + "engine.context.key";

    /**
     * Supplies a single argument to drive the store assertions.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.addArgument("test");
    }

    /**
     * Populates the engine and class stores before tests execute.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        String payload = context.getArgument(String.class);
        System.out.printf("beforeAll(%s)%n", payload);

        context.getClassContext().getEngineContext().getStore().put(ENGINE_CONTEXT_KEY, "engine");
        context.getClassContext().getStore().put(CLASS_CONTEXT_KEY, "class");
        context.getClassContext().getStore().put(argumentKey(context), "argument");
    }

    /**
     * Verifies that values written to each store are readable from the expected scope.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void verifyMaps(final @NonNull ArgumentContext context) {
        String payload = context.getArgument(String.class);
        System.out.printf("verifyMaps(%s)%n", payload);

        Store engineStore = context.getClassContext().getEngineContext().getStore();
        Store classStore = context.getClassContext().getStore();
        assertThat(engineStore.get(ENGINE_CONTEXT_KEY)).isEqualTo("engine");
        assertThat(classStore.get(CLASS_CONTEXT_KEY)).isEqualTo("class");
        assertThat(classStore.get(argumentKey(context))).isEqualTo("argument");

        context.getStore().put(ARGUMENT_CONTEXT_KEY, "argument");
        assertThat(context.getStore().get(ARGUMENT_CONTEXT_KEY)).isEqualTo("argument");
    }

    /**
     * Removes the per-argument key from the class store after all tests complete for the argument.
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
        Object engineRemoved = context.getEngineContext().getStore().remove(ENGINE_CONTEXT_KEY);
        Object classRemoved = context.getStore().remove(CLASS_CONTEXT_KEY);

        assertThat(engineRemoved).isEqualTo("engine");
        assertThat(classRemoved).isEqualTo("class");
    }

    /**
     * Creates a unique class-store key for the provided argument index.
     *
     * @param context for the current argument
     * @return a unique key for the argument index
     */
    private static String argumentKey(final @NonNull ArgumentContext context) {
        return ARGUMENT_CONTEXT_KEY + "." + context.getArgumentIndex();
    }
}
