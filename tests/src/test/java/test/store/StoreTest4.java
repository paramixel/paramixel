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

import java.util.ArrayList;
import java.util.Collection;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class StoreTest4 {

    enum Key {
        ARGUMENT_CONTEXT_KEY;

        public String scopedName() {
            return getClass().getName() + "." + name();
        }
    }

    @Paramixel.ArgumentSupplier(parallelism = Integer.MAX_VALUE)
    public static Iterable<String> arguments() {
        Collection<String> collection = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            collection.add("test-" + i);
        }
        return collection;
    }

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        String payload = argumentContext.getArgument(String.class);
        System.out.printf("beforeAll(%s)%n", payload);
        argumentContext.getClassContext().getStore().put(argumentKey(argumentContext), "argument." + payload);
    }

    @Paramixel.Test
    public void verifyMaps(final @NonNull ArgumentContext argumentContext) {
        String payload = argumentContext.getArgument(String.class);
        System.out.printf("verifyMaps(%s)%n", payload);

        Object value = argumentContext.getClassContext().getStore().get(argumentKey(argumentContext));
        assertThat(value).isEqualTo("argument." + payload);

        argumentContext.getStore().put(Key.ARGUMENT_CONTEXT_KEY.scopedName(), "argument." + payload);
        assertThat(argumentContext.getStore().get(Key.ARGUMENT_CONTEXT_KEY.scopedName()))
                .isEqualTo("argument." + payload);
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        String payload = argumentContext.getArgument(String.class);
        Object removed = argumentContext.getClassContext().getStore().remove(argumentKey(argumentContext));
        assertThat(removed).isEqualTo("argument." + payload);
    }

    private static String argumentKey(final @NonNull ArgumentContext argumentContext) {
        return Key.ARGUMENT_CONTEXT_KEY.scopedName() + "." + argumentContext.getArgumentIndex();
    }
}
