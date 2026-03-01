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

package test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;
import org.paramixel.api.Store;

@Paramixel.TestClass
public class StoreTest1 {

    private static final String PREFIX = StoreTest1.class.getName();
    private static final String ARGUMENT_CONTEXT_KEY = PREFIX + "argument.context.key";
    private static final String CLASS_CONTEXT_KEY = PREFIX + "class.context.key";
    private static final String ENGINE_CONTEXT_KEY = PREFIX + "engine.context.key";

    @Paramixel.ArgumentSupplier
    public static String arguments() {
        return "test";
    }

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        String payload = argumentContext.getArgument(String.class);
        System.out.printf("beforeAll(%s)%n", payload);

        argumentContext.getClassContext().getEngineContext().getStore().put(ENGINE_CONTEXT_KEY, "engine");
        argumentContext.getClassContext().getStore().put(CLASS_CONTEXT_KEY, "class");
        argumentContext.getClassContext().getStore().put(argumentKey(argumentContext), "argument");
    }

    @Paramixel.Test
    public void verifyMaps(final @NonNull ArgumentContext argumentContext) {
        String payload = argumentContext.getArgument(String.class);
        System.out.printf("verifyMaps(%s)%n", payload);

        Store engineStore = argumentContext.getClassContext().getEngineContext().getStore();
        Store classStore = argumentContext.getClassContext().getStore();
        assertThat(engineStore.get(ENGINE_CONTEXT_KEY)).isEqualTo("engine");
        assertThat(classStore.get(CLASS_CONTEXT_KEY)).isEqualTo("class");
        assertThat(classStore.get(argumentKey(argumentContext))).isEqualTo("argument");

        argumentContext.getStore().put(ARGUMENT_CONTEXT_KEY, "argument");
        assertThat(argumentContext.getStore().get(ARGUMENT_CONTEXT_KEY)).isEqualTo("argument");
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        Object removed = argumentContext.getClassContext().getStore().remove(argumentKey(argumentContext));
        assertThat(removed).isEqualTo("argument");
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext classContext) {
        Object engineRemoved = classContext.getEngineContext().getStore().remove(ENGINE_CONTEXT_KEY);
        Object classRemoved = classContext.getStore().remove(CLASS_CONTEXT_KEY);

        assertThat(engineRemoved).isEqualTo("engine");
        assertThat(classRemoved).isEqualTo("class");
    }

    private static String argumentKey(final @NonNull ArgumentContext argumentContext) {
        return ARGUMENT_CONTEXT_KEY + "." + argumentContext.getArgumentIndex();
    }
}
