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

import java.util.Collections;
import java.util.List;
import java.util.Vector;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that arguments can be supplied by iterating over an {@code Enumeration}.
 */
public class EnumerationArgumentsTest {

    /**
     * Builds a {@link Vector} and supplies its elements as arguments.
     *
     * @param collector the arguments collector
     */
    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        Vector<String> vector = new Vector<>();
        vector.add("test1");
        vector.add("test2");

        List<String> list = Collections.list(vector.elements());
        for (String item : list) {
            collector.addArgument(item);
        }
    }

    /**
     * Asserts that the argument payload is a {@link String}.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(String.class);
    }
}
