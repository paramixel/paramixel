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
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class ArrayOfMixedObjectsArgumentTest {

    @Paramixel.ArgumentSupplier
    public static Object[] arguments() {
        Object[] objects = new Object[14];
        int i = 0;
        objects[i++] = null;
        objects[i++] = Boolean.TRUE;
        objects[i++] = (short) 1;
        objects[i++] = (byte) 2;
        objects[i++] = 'x';
        objects[i++] = 4;
        objects[i++] = 5L;
        objects[i++] = 6f;
        objects[i++] = 7d;
        objects[i++] = new BigInteger("8");
        objects[i++] = new BigDecimal("9");
        objects[i++] = "test";
        objects[i++] = new Date();
        objects[i] = NamedValue.of("test", "test");
        return objects;
    }

    @Paramixel.Test
    public void test(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();

        Object argument = argumentContext.getArgument();
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
