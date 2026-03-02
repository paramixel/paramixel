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

package test.named;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

/**
 * Exercises {@link NamedValue#getValue(Class)} for matching and mismatched types.
 */
@Paramixel.TestClass
public class NamedValueTest2 {

    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.addArgument(NamedValue.of("int value", 42));
    }

    @Paramixel.Test
    public void castsToRequestedType(final @NonNull ArgumentContext context) {
        NamedValue<?> argument = context.getArgument(NamedValue.class);
        String name = argument.getName();
        Integer value = argument.getValue(Integer.class);

        assertThat(name).isEqualTo("int value");
        assertThat(value).isEqualTo(42);
    }
}
