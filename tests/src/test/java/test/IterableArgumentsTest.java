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

package test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class IterableArgumentsTest {

    private static final Set<String> seen = ConcurrentHashMap.newKeySet();

    @Paramixel.ArgumentSupplier
    public static Iterable<Object> arguments() {
        return new StringIterable(new String[] {"iter1", "iter2"});
    }

    @Paramixel.Test
    public void test(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext.getStore()).isNotNull();
        Object argument = argumentContext.getArgument();
        assertThat(argument).isInstanceOf(String.class);
        seen.add((String) argument);
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext classContext) {
        assertThat(seen).contains("iter1", "iter2");
    }

    private static final class StringIterable implements Iterable<Object> {

        private final String[] values;

        private StringIterable(final String[] values) {
            this.values = values;
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < values.length;
                }

                @Override
                public Object next() {
                    return values[index++];
                }
            };
        }
    }
}
