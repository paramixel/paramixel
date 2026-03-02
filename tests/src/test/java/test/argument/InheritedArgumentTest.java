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
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Named;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies handling of arguments whose runtime type inherits from an abstract base type.
 */
public class InheritedArgumentTest {

    /**
     * Supplies concrete argument instances that extend an abstract base class.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        for (int i = 0; i < 10; i++) {
            argumentSupplierContext.addArgument(new ConcreteCustomArgument(i));
        }
    }

    /**
     * Verifies that the class context is available.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
    }

    /**
     * Asserts the argument is the concrete type.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(1)
    public void testDirectArgument1(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isInstanceOf(ConcreteCustomArgument.class);
    }

    /**
     * Asserts the argument is also assignable to the abstract base type.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(2)
    public void testDirectArgument2(final @NonNull ArgumentContext context) {
        assertThat(context.getArgument()).isInstanceOf(AbstractCustomArgument.class);
    }

    /**
     * Verifies that {@link NamedValue} can wrap the argument using its {@link Named} name.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(3)
    public void testArgument(final @NonNull ArgumentContext context) {
        ConcreteCustomArgument argument = (ConcreteCustomArgument) context.getArgument();
        NamedValue<ConcreteCustomArgument> namedValue = NamedValue.of(argument.getName(), argument);
        assertThat(namedValue.getName()).isEqualTo(argument.getName());
        assertThat(namedValue.getValue()).isSameAs(argument);
    }

    /**
     * Asserts that the argument implements {@link Named} and that its name matches expectations.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(4)
    public void testArgumentContext(final @NonNull ArgumentContext context) {
        Object argument = context.getArgument();
        assertThat(argument).isInstanceOf(ConcreteCustomArgument.class);
        assertThat(argument).isInstanceOf(AbstractCustomArgument.class);
        assertThat(argument).isInstanceOf(Named.class);
        assertThat(((Named) argument).getName()).startsWith("CustomArgument(");
    }

    /**
     * Verifies that the class context is available during finalize.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
    }

    /**
     * Concrete argument implementation used for inheritance tests.
     */
    public static final class ConcreteCustomArgument extends AbstractCustomArgument {

        /**
         * Creates a new instance.
         *
         * @param value payload used for naming
         */
        public ConcreteCustomArgument(final int value) {
            super(value);
        }
    }

    /**
     * Abstract base type for arguments that provide a stable name.
     */
    public abstract static class AbstractCustomArgument implements Named {

        /** Value used to generate the name. */
        private final int value;

        /**
         * Creates a new instance.
         *
         * @param value payload used for naming
         */
        protected AbstractCustomArgument(final int value) {
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
