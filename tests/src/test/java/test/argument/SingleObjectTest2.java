/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies delivery of multiple integer arguments and basic {@link NamedValue} wrapping.
 */
public class SingleObjectTest2 {

    /**
     * Supplies a sequence of integer arguments.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {

        for (int i = 0; i < 10; i++) {
            collector.addArgument(i);
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
     * Asserts that the direct argument is an {@link Integer}.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(1)
    public void testDirectArgument(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isInstanceOf(Integer.class);
    }

    /**
     * Wraps the argument in a {@link NamedValue} and asserts the value is preserved.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(2)
    public void testArgument(final @NonNull ArgumentContext context) {
        Integer value = (Integer) context.getArgument();
        NamedValue<Integer> namedValue = NamedValue.of(String.valueOf(value), value);
        assertThat(namedValue.getValue()).isEqualTo(value);
    }

    /**
     * Asserts that the argument is an {@link Integer} when accessed via the context.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(3)
    public void testArgumentContext(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isInstanceOf(Integer.class);
    }

    /**
     * Verifies that the class context is available during finalize.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
    }
}
