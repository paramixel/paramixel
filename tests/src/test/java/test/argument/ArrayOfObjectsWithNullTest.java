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
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies handling of {@code null} objects supplied as arguments.
 */
public class ArrayOfObjectsWithNullTest {

    /**
     * Supplies arguments including {@code null} values.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.addArgument(null);
        argumentSupplierContext.addArgument("test1");
        argumentSupplierContext.addArgument(null);
    }

    /**
     * Asserts that non-null arguments are strings matching the expected prefix.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();

        Object argument = context.getArgument();
        if (argument == null) {
            return;
        }

        assertThat(argument).isInstanceOf(String.class);
        assertThat((String) argument).startsWith("test");
    }
}
