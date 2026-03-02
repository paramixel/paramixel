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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies delivery of mixed-type arguments supplied via multiple {@code addArgument} calls.
 */
public class ArrayOfMixedObjectsArgumentTest {

    /**
     * Supplies a heterogeneous set of arguments.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.addArgument(null);
        argumentSupplierContext.addArgument(Boolean.TRUE);
        argumentSupplierContext.addArgument((short) 1);
        argumentSupplierContext.addArgument((byte) 2);
        argumentSupplierContext.addArgument('x');
        argumentSupplierContext.addArgument(4);
        argumentSupplierContext.addArgument(5L);
        argumentSupplierContext.addArgument(6f);
        argumentSupplierContext.addArgument(7d);
        argumentSupplierContext.addArgument(new BigInteger("8"));
        argumentSupplierContext.addArgument(new BigDecimal("9"));
        argumentSupplierContext.addArgument("test");
        argumentSupplierContext.addArgument(new Date());
        argumentSupplierContext.addArgument(NamedValue.of("test", "test"));
    }

    /**
     * Asserts that any non-null argument is one of the expected types.
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

        assertThat(argument)
                .isInstanceOfAny(
                        Boolean.class,
                        Short.class,
                        Byte.class,
                        Character.class,
                        Integer.class,
                        Long.class,
                        Float.class,
                        Double.class,
                        BigInteger.class,
                        BigDecimal.class,
                        String.class,
                        Date.class,
                        NamedValue.class);
    }
}
