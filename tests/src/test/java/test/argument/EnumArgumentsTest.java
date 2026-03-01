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

import java.util.Arrays;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class EnumArgumentsTest {

    public enum EnumArgument {
        ZERO,
        ONE,
        TWO
    }

    @Paramixel.ArgumentSupplier
    public static Object arguments() {
        return Arrays.asList(EnumArgument.ZERO, EnumArgument.ONE, EnumArgument.TWO);
    }

    @Paramixel.Test
    @Paramixel.Order(1)
    public void testDirectArgument(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(EnumArgument.class);
    }

    @Paramixel.Test
    @Paramixel.Order(2)
    public void testArgumentContext(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(EnumArgument.class);

        EnumArgument enumArgument = (EnumArgument) argumentContext.getArgument();

        switch (argumentContext.getArgumentIndex()) {
            case 0 -> assertThat(enumArgument).isEqualTo(EnumArgument.ZERO);
            case 1 -> assertThat(enumArgument).isEqualTo(EnumArgument.ONE);
            case 2 -> assertThat(enumArgument).isEqualTo(EnumArgument.TWO);
            default ->
                throw new IllegalStateException("unexpected argumentIndex: " + argumentContext.getArgumentIndex());
        }
    }
}
