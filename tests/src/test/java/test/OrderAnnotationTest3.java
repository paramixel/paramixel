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

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class OrderAnnotationTest3 {

    private static final String ORDER_KEY = "order-annotation-test";

    @Paramixel.ArgumentSupplier(parallelism = 1)
    public static String arguments() {
        return "single";
    }

    @Paramixel.Initialize
    public void initialize(final ClassContext classContext) {
        classContext.getStore().put(ORDER_KEY, new ArrayList<String>());
    }

    @Paramixel.Test
    @Paramixel.Order(2)
    public void zeta(final @NonNull ArgumentContext argumentContext) {
        append(argumentContext, "zeta");
    }

    @Paramixel.Test
    @Paramixel.Order(2)
    public void alpha(final @NonNull ArgumentContext argumentContext) {
        append(argumentContext, "alpha");
    }

    @Paramixel.Test
    @Paramixel.Order(1)
    public void beta(final @NonNull ArgumentContext argumentContext) {
        append(argumentContext, "beta");
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext classContext) {
        @SuppressWarnings("unchecked")
        List<String> order = classContext.getStore().get(ORDER_KEY, List.class);
        assertThat(order).isNotNull().containsExactly("beta", "alpha", "zeta");
    }

    private static void append(final ArgumentContext argumentContext, final String value) {
        @SuppressWarnings("unchecked")
        List<String> order = argumentContext.getClassContext().getStore().get(ORDER_KEY, List.class);
        assertThat(order).isNotNull();
        order.add(value);
    }
}
