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
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies per-argument keying in the class store when multiple arguments are supplied.
 */
public class StoreTest4 {

    /**
     * Keys used to create scoped store key names.
     */
    enum Key {

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
     * Supplies multiple string arguments.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {

        for (int i = 0; i < 10; i++) {
            argumentSupplierContext.addArgument("test-" + i);
        }
    }

    /**
     * Stores a per-argument value in the class store.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        String payload = context.getArgument(String.class);
        System.out.printf("beforeAll(%s)%n", payload);
        context.getClassContext().getStore().put(argumentKey(context), "argument." + payload);
    }

    /**
     * Verifies the stored value for the current argument.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void verifyMaps(final @NonNull ArgumentContext context) {
        String payload = context.getArgument(String.class);
        System.out.printf("verifyMaps(%s)%n", payload);

        Object value = context.getClassContext().getStore().get(argumentKey(context));
        assertThat(value).isEqualTo("argument." + payload);

        context.getStore().put(Key.ARGUMENT_CONTEXT_KEY.scopedName(), "argument." + payload);
        assertThat(context.getStore().get(Key.ARGUMENT_CONTEXT_KEY.scopedName()))
                .isEqualTo("argument." + payload);
    }

    /**
     * Removes the per-argument value from the class store.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        String payload = context.getArgument(String.class);
        Object removed = context.getClassContext().getStore().remove(argumentKey(context));
        assertThat(removed).isEqualTo("argument." + payload);
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
