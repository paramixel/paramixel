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

import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies storing and closing {@link AutoCloseable} values across store scopes.
 *
 * <p>This test places {@link AutoCloseable} values in engine, class, and argument stores and then
 * removes and closes them during lifecycle callbacks.
 */
public class StoreAutoCloseableTest {

    /**
     * Supplies a small set of arguments for repeated store interactions.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        for (int i = 0; i < 2; i++) {
            collector.addArgument("String " + i);
        }
    }

    /**
     * Verifies that the class context and store are available.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Populates engine and class stores with {@link AutoCloseable} values.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        System.out.printf("beforeAll(%s)%n", context.getArgument());

        context.getClassContext().getEngineContext().getStore().put("autoCloseable", new CustomAutoCloseable("engine"));
        context.getClassContext().getStore().put("autoCloseable", new CustomAutoCloseable("class"));
        context.getClassContext().getStore().put(argumentKey(context), new CustomAutoCloseable("argument"));

        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Validates that the argument context is available before each invocation.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Stores and immediately closes an argument-scoped {@link AutoCloseable} for test method 1.
     *
     * @param context for the current argument
     * @throws InterruptedException if the sleep is interrupted
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) throws InterruptedException {
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
        context.getStore().put("autoCloseable", new CustomAutoCloseable("argument"));
        closeIfPresent(context.getStore().remove("autoCloseable"));
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
    }

    /**
     * Stores and immediately closes an argument-scoped {@link AutoCloseable} for test method 2.
     *
     * @param context for the current argument
     * @throws InterruptedException if the sleep is interrupted
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) throws InterruptedException {
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
        context.getStore().put("autoCloseable", new CustomAutoCloseable("argument"));
        closeIfPresent(context.getStore().remove("autoCloseable"));
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, 25));
    }

    /**
     * Validates that the argument context is available after each invocation.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
    }

    /**
     * Removes and closes the argument-scoped {@link AutoCloseable} stored in the class store.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();
        closeIfPresent(context.getClassContext().getStore().remove(argumentKey(context)));
    }

    /**
     * Removes and closes engine and class scoped {@link AutoCloseable} values.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        closeIfPresent(context.getStore().remove("autoCloseable"));
        closeIfPresent(context.getEngineContext().getStore().remove("autoCloseable"));
    }

    /**
     * Closes a value if it implements {@link AutoCloseable}.
     *
     * @param value value to close
     */
    private static void closeIfPresent(final Object value) {
        if (value instanceof AutoCloseable) {
            try {
                ((AutoCloseable) value).close();
            } catch (Exception ex) {
                throw new IllegalStateException("failed to close autoCloseable", ex);
            }
        }
    }

    /**
     * Builds a unique per-argument key for storing argument-scoped values in the class store.
     *
     * @param context for the current argument
     * @return a key including the argument index
     */
    private static String argumentKey(final @NonNull ArgumentContext context) {
        return "autoCloseable.argument." + context.getArgumentIndex();
    }

    /**
     * Simple {@link AutoCloseable} implementation for store testing.
     */
    private static class CustomAutoCloseable implements AutoCloseable {

        /** Name used for log output in {@link #toString()}. */
        private final String name;

        /**
         * Creates a new instance.
         *
         * @param name identifier used for log output
         */
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
