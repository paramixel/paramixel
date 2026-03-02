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
 * Verifies handling of {@code null} arguments mixed with custom {@link Named} arguments.
 */
public class CustomArgumentWithNullTest {

    /**
     * Supplies alternating {@code null} and custom argument values.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.setParallelism(2);
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                collector.addArgument(null);
            } else {
                collector.addArgument(new CustomArgument("String " + i));
            }
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
     * Verifies that the argument context and store are available.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Verifies that the argument context and store are available.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Validates non-null arguments for the first test method.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();

        Object argument = context.getArgument();
        if (argument == null) {
            return;
        }

        assertThat(argument).isInstanceOf(CustomArgument.class);
        assertThat(((CustomArgument) argument).getName()).startsWith("CustomArgument(");
    }

    /**
     * Validates non-null arguments for the second test method.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();

        Object argument = context.getArgument();
        if (argument == null) {
            return;
        }

        assertThat(argument).isInstanceOf(CustomArgument.class);
        assertThat(argument).isInstanceOf(Named.class);
    }

    /**
     * Verifies that the argument context and store are available.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Verifies that the argument context and store are available.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
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
     * Custom {@link Named} argument implementation.
     */
    public static final class CustomArgument implements Named {

        /** Value used to generate the name. */
        private final String value;

        /**
         * Creates a new argument.
         *
         * @param value payload used for naming
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
