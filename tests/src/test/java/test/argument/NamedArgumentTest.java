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

import java.util.ArrayList;
import java.util.Collection;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Named;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class NamedArgumentTest {

    @Paramixel.ArgumentSupplier
    public static Collection<Object> arguments() {
        Collection<Object> collection = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            collection.add(new CustomArgument(i));
        }
        return collection;
    }

    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext classContext) {
        assertThat(classContext).isNotNull();
    }

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(CustomArgument.class);
    }

    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(CustomArgument.class);
    }

    @Paramixel.Test
    @Paramixel.Order(1)
    public void testDirectArgument(final @NonNull ArgumentContext argumentContext) {
        CustomArgument argument = (CustomArgument) argumentContext.getArgument();
        assertThat(argument.getName()).startsWith("CustomArgument(");
    }

    @Paramixel.Test
    @Paramixel.Order(3)
    public void testArgument(final @NonNull ArgumentContext argumentContext) {
        CustomArgument argument = (CustomArgument) argumentContext.getArgument();
        NamedValue<CustomArgument> namedValue = NamedValue.of(argument.getName(), argument);
        assertThat(namedValue.getName()).isEqualTo(argument.getName());
        assertThat(namedValue.getValue()).isSameAs(argument);
    }

    @Paramixel.Test
    @Paramixel.Order(4)
    public void testArgumentContext(final @NonNull ArgumentContext argumentContext) {
        CustomArgument argument = (CustomArgument) argumentContext.getArgument();
        assertThat(argument).isInstanceOf(Named.class);
        assertThat(((Named) argument).getName()).isEqualTo(argument.getName());
    }

    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(CustomArgument.class);
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(CustomArgument.class);
    }

    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext classContext) {
        assertThat(classContext).isNotNull();
    }

    public static final class CustomArgument implements Named {

        private final int value;

        public CustomArgument(final int value) {
            this.value = value;
        }

        @Override
        public String getName() {
            return "CustomArgument(" + value + ")";
        }

        @Override
        public String toString() {
            return "CustomArgument{" + "value=" + value + '}';
        }
    }
}
