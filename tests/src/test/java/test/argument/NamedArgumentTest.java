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
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that arguments implementing {@link Named} surface their names consistently.
 */
public class NamedArgumentTest {

    /**
     * Supplies {@link Named} argument instances.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        for (int i = 0; i < 10; i++) {
            collector.addArgument(new CustomArgument(i));
        }
    }

    /**
     * Verifies that the class context is available.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
    }

    /**
     * Asserts that the current argument is a {@link CustomArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Asserts that the current argument is a {@link CustomArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the direct argument name follows the expected pattern.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(1)
    public void testDirectArgument(final @NonNull ArgumentContext context) {
        CustomArgument argument = (CustomArgument) context.getArgument();
        assertThat(argument.getName()).startsWith("CustomArgument(");
    }

    /**
     * Verifies that a {@link NamedValue} can wrap a {@link Named} argument using its name.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(3)
    public void testArgument(final @NonNull ArgumentContext context) {
        CustomArgument argument = (CustomArgument) context.getArgument();
        NamedValue<CustomArgument> namedValue = NamedValue.of(argument.getName(), argument);
        assertThat(namedValue.getName()).isEqualTo(argument.getName());
        assertThat(namedValue.getValue()).isSameAs(argument);
    }

    /**
     * Verifies that the argument also satisfies {@link Named} when accessed through the context.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(4)
    public void testArgumentContext(final @NonNull ArgumentContext context) {
        CustomArgument argument = (CustomArgument) context.getArgument();
        assertThat(argument).isInstanceOf(Named.class);
        assertThat(((Named) argument).getName()).isEqualTo(argument.getName());
    }

    /**
     * Asserts that the current argument is a {@link CustomArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Asserts that the current argument is a {@link CustomArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the class context is available.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
    }

    /**
     * Simple {@link Named} argument implementation for name-based tests.
     */
    public static final class CustomArgument implements Named {

        /** Value used to generate the name. */
        private final int value;

        /**
         * Creates a new argument.
         *
         * @param value payload used for naming
         */
        public CustomArgument(final int value) {
            this.value = value;
        }

        @Override
        public String getName() {
            return "CustomArgument(" + value + ")";
        }

        @Override
        public String toString() {
            return "CustomArgument{" + "value=" + value + '}';
        }
    }
}
