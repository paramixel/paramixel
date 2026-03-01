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

import java.util.Arrays;
import java.util.Collection;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Named;
import org.paramixel.api.Paramixel;

/**
 * Demonstrates the Named argument display name contract.
 */
@Paramixel.TestClass
public class NamedTest {

    /**
     * Supplies named arguments for parameterized execution.
     *
     * @return the collection of named arguments
     */
    @Paramixel.ArgumentSupplier
    public static Collection<NamedString> arguments() {
        System.out.println("[ARGUMENT_SUPPLIER] Providing named arguments");
        return Arrays.asList(
                new NamedString("First Argument"),
                new NamedString("Second Argument"),
                new NamedString("Third Argument"));
    }

    /**
     * Validates that arguments implement {@link Named}.
     *
     * @param argumentContext the argument context
     */
    @Paramixel.Test
    public void testWithNamedArgument(final @NonNull ArgumentContext argumentContext) {
        Object argument = argumentContext.getArgument();
        System.out.println("[TEST_METHOD] argument class: " + argument.getClass());
        System.out.println("[TEST_METHOD] argument value: " + argument);
        System.out.println("[TEST_METHOD] is NamedString: " + (argument instanceof NamedString));
        System.out.println("[TEST_METHOD] is Named: " + (argument instanceof Named));
        assertThat(argument).isInstanceOf(NamedString.class);

        Named named = (Named) argument;
        System.out.println("[TEST_METHOD] argument name: " + named.getName());
        assertThat(named.getName()).endsWith(" Argument");
    }

    /**
     * Simple {@link Named} implementation backed by a string.
     *
     * @param getName Display name value.
     */
    public record NamedString(String getName) implements Named {

        /**
         * Creates a new named string.
         *
         * @param getName the display name
         */
        public NamedString {}

        @Override
        public String toString() {
            return getName;
        }
    }
}
