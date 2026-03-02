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
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies delivery of enum arguments and access by argument index.
 */
public class EnumArgumentsTest {

    /** Enum values used as test arguments. */
    public enum EnumArgument {

        /** First value. */
        ZERO,

        /** Second value. */
        ONE,

        /** Third value. */
        TWO
    }

    /**
     * Supplies all {@link EnumArgument} values as arguments.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArguments(EnumArgument.ZERO, EnumArgument.ONE, EnumArgument.TWO);
    }

    /**
     * Asserts that the direct argument is an {@link EnumArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(1)
    public void testDirectArgument(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(EnumArgument.class);
    }

    /**
     * Asserts that the argument value matches the expected enum constant for the current index.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(2)
    public void testArgumentContext(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(EnumArgument.class);

        EnumArgument enumArgument = (EnumArgument) context.getArgument();

        switch (context.getArgumentIndex()) {
            case 0 -> assertThat(enumArgument).isEqualTo(EnumArgument.ZERO);
            case 1 -> assertThat(enumArgument).isEqualTo(EnumArgument.ONE);
            case 2 -> assertThat(enumArgument).isEqualTo(EnumArgument.TWO);
            default -> throw new IllegalStateException("unexpected argumentIndex: " + context.getArgumentIndex());
        }
    }
}
