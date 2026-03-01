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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class CollectionOfMixedObjectsArgumentTest {

    @Paramixel.ArgumentSupplier
    public static Collection<Object> arguments() {
        Collection<Object> collection = new ArrayList<>();
        collection.add(null);
        collection.add(Boolean.TRUE);
        collection.add((short) 1);
        collection.add((byte) 2);
        collection.add('x');
        collection.add(4);
        collection.add(5L);
        collection.add(6f);
        collection.add(7d);
        collection.add(new BigInteger("8"));
        collection.add(new BigDecimal("9"));
        collection.add("test");
        collection.add(new Date());
        collection.add(NamedValue.of("test", "test"));
        return collection;
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
