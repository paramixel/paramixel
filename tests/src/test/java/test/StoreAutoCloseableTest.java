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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class StoreAutoCloseableTest {

    @Paramixel.ArgumentSupplier
    public static Collection<String> arguments() {
        Collection<String> collection = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            collection.add("String " + i);
        }
        return collection;
    }

    @Paramixel.Initialize
    public void initialize(final ClassContext classContext) {
        assertThat(classContext).isNotNull();
        assertThat(classContext.getStore()).isNotNull();
    }

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        System.out.printf("beforeAll(%s)%n", argumentContext.getArgument());

        argumentContext
                .getClassContext()
                .getEngineContext()
                .getStore()
                .put("autoCloseable", new CustomAutoCloseable("engine"));
        argumentContext.getClassContext().getStore().put("autoCloseable", new CustomAutoCloseable("class"));
        argumentContext
                .getClassContext()
                .getStore()
                .put(argumentKey(argumentContext), new CustomAutoCloseable("argument"));

        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext argumentContext) throws InterruptedException {
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
        argumentContext.getStore().put("autoCloseable", new CustomAutoCloseable("argument"));
        closeIfPresent(argumentContext.getStore().remove("autoCloseable"));
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
    }

    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext argumentContext) throws InterruptedException {
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
        argumentContext.getStore().put("autoCloseable", new CustomAutoCloseable("argument"));
        closeIfPresent(argumentContext.getStore().remove("autoCloseable"));
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
    }

    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();
        closeIfPresent(argumentContext.getClassContext().getStore().remove(argumentKey(argumentContext)));
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext classContext) {
        assertThat(classContext).isNotNull();
        assertThat(classContext.getStore()).isNotNull();
        closeIfPresent(classContext.getStore().remove("autoCloseable"));
        closeIfPresent(classContext.getEngineContext().getStore().remove("autoCloseable"));
    }

    private static void closeIfPresent(final Object value) {
        if (value instanceof AutoCloseable) {
            try {
                ((AutoCloseable) value).close();
            } catch (Exception ex) {
                throw new IllegalStateException("failed to close autoCloseable", ex);
            }
        }
    }

    private static String argumentKey(final @NonNull ArgumentContext argumentContext) {
        return "autoCloseable.argument." + argumentContext.getArgumentIndex();
    }

    private static class CustomAutoCloseable implements AutoCloseable {

        private final String name;

        private CustomAutoCloseable(final String name) {
            this.name = name;
        }

        @Override
        public void close() {
            System.out.printf("%s close()%n", this);
        }

        @Override
        public String toString() {
            return "CustomAutoCloseable{" + "name='" + name + '\'' + '}';
        }
    }
}
