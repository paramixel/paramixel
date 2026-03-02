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

import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Named;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies that {@link AutoCloseable} arguments are closed after execution.
 */
public class AutoCloseableCustomArgumentTest {

    /** Counts how many times {@link CustomArgument#close()} is invoked. */
    private static final AtomicInteger CLOSE_COUNT = new AtomicInteger(0);

    /**
     * Supplies a single {@link AutoCloseable} custom argument.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.addArgument(new CustomArgument("String 0"));
    }

    /**
     * Resets the close counter and verifies the class context is available.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext context) {
        CLOSE_COUNT.set(0);
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
    }

    /**
     * Asserts that the provided argument is a {@link CustomArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Asserts that the provided argument is a {@link CustomArgument}.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getStore()).isNotNull();
        assertThat(context.getArgument()).isInstanceOf(CustomArgument.class);
    }

    /**
     * Verifies that the {@link AutoCloseable} argument was closed exactly once.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext context) {
        assertThat(context).isNotNull();
        assertThat(CLOSE_COUNT.get()).isEqualTo(1);
    }

    /**
     * Custom argument that is both {@link Named} and {@link AutoCloseable}.
     */
    public static final class CustomArgument implements Named, AutoCloseable {

        /** Value returned as the argument name. */
        private final String value;

        /**
         * Creates a new argument instance.
         *
         * @param value payload used for naming
         */
        public CustomArgument(final @NonNull String value) {
            this.value = value;
        }

        @Override
        public String getName() {
            return value;
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public void close() {
            CLOSE_COUNT.incrementAndGet();
        }
    }
}
