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

@Paramixel.TestClass
public class StoreTest3 {

    enum Key {
        ENGINE_CONTEXT_KEY,
        CLASS_CONTEXT_KEY,
        ARGUMENT_CONTEXT_KEY;

        public String scopedName() {
            return getClass().getName() + "." + name();
        }
    }

    @Paramixel.ArgumentSupplier
    public static String arguments() {
        return "test";
    }

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        String payload = argumentContext.getArgument(String.class);
        System.out.printf("beforeAll(%s)%n", payload);

        argumentContext
                .getClassContext()
                .getEngineContext()
                .getStore()
                .put(Key.ENGINE_CONTEXT_KEY.scopedName(), "engine");
        argumentContext.getClassContext().getStore().put(Key.CLASS_CONTEXT_KEY.scopedName(), "class");
        argumentContext.getClassContext().getStore().put(argumentKey(argumentContext), "argument");
    }

    @Paramixel.Test
    public void verifyMaps(final @NonNull ArgumentContext argumentContext) {
        String payload = argumentContext.getArgument(String.class);
        System.out.printf("verifyMaps(%s)%n", payload);

        Object engineValue = argumentContext
                .getClassContext()
                .getEngineContext()
                .getStore()
                .get(Key.ENGINE_CONTEXT_KEY.scopedName());
        Object classValue = argumentContext.getClassContext().getStore().get(Key.CLASS_CONTEXT_KEY.scopedName());
        Object argumentValue = argumentContext.getClassContext().getStore().get(argumentKey(argumentContext));

        assertThat(engineValue).isEqualTo("engine");
        assertThat(classValue).isEqualTo("class");
        assertThat(argumentValue).isEqualTo("argument");

        argumentContext.getStore().put(Key.ARGUMENT_CONTEXT_KEY.scopedName(), "argument");
        assertThat(argumentContext.getStore().get(Key.ARGUMENT_CONTEXT_KEY.scopedName()))
                .isEqualTo("argument");
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        Object removed = argumentContext.getClassContext().getStore().remove(argumentKey(argumentContext));
        assertThat(removed).isEqualTo("argument");
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext classContext) {
        Object engineRemoved = classContext.getEngineContext().getStore().remove(Key.ENGINE_CONTEXT_KEY.scopedName());
        Object classRemoved = classContext.getStore().remove(Key.CLASS_CONTEXT_KEY.scopedName());

        assertThat(engineRemoved).isEqualTo("engine");
        assertThat(classRemoved).isEqualTo("class");
    }

    private static String argumentKey(final @NonNull ArgumentContext argumentContext) {
        return Key.ARGUMENT_CONTEXT_KEY.scopedName() + "." + argumentContext.getArgumentIndex();
    }
}
