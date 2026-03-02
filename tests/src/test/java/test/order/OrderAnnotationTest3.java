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

package test.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentSupplierContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
/**
 * Verifies ordering behavior when multiple test methods share the same {@link Paramixel.Order}.
 */
public class OrderAnnotationTest3 {

    /** Store key for the mutable list tracking observed method execution order. */
    private static final String ORDER_KEY = "order-annotation-test";

    /**
     * Supplies a single argument and forces sequential execution for deterministic ordering.
     *
     * @param argumentSupplierContext context used to register test arguments
     */
    @Paramixel.ArgumentSupplier
    public static void arguments(final @NonNull ArgumentSupplierContext argumentSupplierContext) {
        argumentSupplierContext.setParallelism(1);
        argumentSupplierContext.addArgument("single");
    }

    /**
     * Initializes the class store with an empty list used for order tracking.
     *
     * @param context for the current class
     */
    @Paramixel.Initialize
    public void initialize(final ClassContext context) {
        context.getStore().put(ORDER_KEY, new ArrayList<String>());
    }

    /**
     * Appends "zeta" to the observed execution order.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(2)
    public void zeta(final @NonNull ArgumentContext context) {
        append(context, "zeta");
    }

    /**
     * Appends "alpha" to the observed execution order.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(2)
    public void alpha(final @NonNull ArgumentContext context) {
        append(context, "alpha");
    }

    /**
     * Appends "beta" to the observed execution order.
     *
     * @param context for the current argument
     */
    @Paramixel.Test
    @Paramixel.Order(1)
    public void beta(final @NonNull ArgumentContext context) {
        append(context, "beta");
    }

    /**
     * Asserts that method invocations occurred in the expected order.
     *
     * @param context for the current class
     */
    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        @SuppressWarnings("unchecked")
        List<String> order = context.getStore().get(ORDER_KEY, List.class);
        assertThat(order).isNotNull().containsExactly("beta", "alpha", "zeta");
    }

    /**
     * Adds a value to the per-class order list.
     *
     * @param context for the current argument
     * @param value value to append
     */
    private static void append(final ArgumentContext context, final String value) {
        @SuppressWarnings("unchecked")
        List<String> order = context.getClassContext().getStore().get(ORDER_KEY, List.class);
        assertThat(order).isNotNull();
        order.add(value);
    }
}
