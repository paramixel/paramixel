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
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class ArrayOfArgumentsTest {

    @Paramixel.ArgumentSupplier
    public static Object[] arguments() {
        NamedValue<?>[] arguments = new NamedValue[3];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = NamedValue.of("test" + i, "test" + i);
        }
        return arguments;
    }

    @Paramixel.Test
    public void test(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isNotNull();

        Object raw = argumentContext.getArgument();
        assertThat(raw).isInstanceOf(NamedValue.class);

        @SuppressWarnings("unchecked")
        NamedValue<String> argument = (NamedValue<String>) raw;

        assertThat(argument.getName()).startsWith("test");
        assertThat(argument.getValue()).startsWith("test");
    }
}
