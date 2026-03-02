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

package test.argument;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Named;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that custom argument objects can be supplied and identified via {@link Named}.
 */
public class CustomArgumentTest {

    /**
     * Supplies custom argument instances and configures argument-level parallelism.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(2);
        for (int i = 0; i < 10; i++) {
            collector.addArgument(new CustomArgument("String " + i));
        }
    }

    /**
     * Verifies that the class context and store are available.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Verifies that the argument is a {@link CustomArgument} before tests run.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the argument is a {@link CustomArgument} before each invocation.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the argument is a {@link CustomArgument} for the first test method.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the argument is a {@link CustomArgument} for the second test method.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the argument is a {@link CustomArgument} after each invocation.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the argument is a {@link CustomArgument} after all tests for an argument.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the class context and store remain available during finalize.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Custom argument payload used to exercise {@link Named} support.
     */
    public static final class CustomArgument implements Named {

        /** Payload used to generate a stable name. */
        private final String value;

        /**
         * Creates a new custom argument.
         *
         * @param value payload to associate with this argument
         */
        public CustomArgument(final @NonNull String value) {
            this.value = value;
        }

        @Override
        public String getName() {
            return "CustomArgument(" + value + ")";
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
