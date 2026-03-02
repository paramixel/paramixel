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
import org.paramixel.api.Named;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

/**
 * Verifies {@link NamedValue} preserves names and values.
 */
@Paramixel.TestClass
public class NamedValueTest {

    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.addArgument(NamedValue.of("alpha", "A"));
        argumentSupplierContext.addArgument(NamedValue.of("beta", "B"));
    }

    @Paramixel.Test
    public void retainsNameAndValue(final @NonNull ArgumentContext context) {
        Object raw = context.getArgument();
        assertThat(raw).isInstanceOf(NamedValue.class).isInstanceOf(Named.class);

        @SuppressWarnings("unchecked")
        NamedValue<String> argument = (NamedValue<String>) raw;
        String name = argument.getName();
        String value = argument.getValue();

        assertThat(name).isIn("alpha", "beta");
        if ("alpha".equals(name)) {
            assertThat(value).isEqualTo("A");
        } else {
            assertThat(value).isEqualTo("B");
        }
    }
}
