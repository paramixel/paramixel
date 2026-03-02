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
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies handling of {@link NamedValue} arguments with {@code null} values.
 */
public class ArrayOfArgumentsWithNullTest {

    /**
     * Supplies a small set of {@link NamedValue} arguments, alternating {@code null} and non-null values.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        for (int i = 0; i < 3; i++) {
            if (i % 2 == 0) {
                argumentSupplierContext.addArgument(NamedValue.of("test" + i, null));
            } else {
                argumentSupplierContext.addArgument(NamedValue.of("test" + i, "test" + i));
            }
        }
    }

    /**
     * Asserts that the argument is a {@link NamedValue} and validates non-null values.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isNotNull();

        Object raw = context.getArgument();
        assertThat(raw).isInstanceOf(NamedValue.class);

        @SuppressWarnings("unchecked")
        NamedValue<String> argument = (NamedValue<String>) raw;

        if (argument.getValue() == null) {
            return;
        }

        assertThat(argument.getValue()).startsWith("test");
    }
}
