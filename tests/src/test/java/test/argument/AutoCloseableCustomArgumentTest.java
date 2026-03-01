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
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Named;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class AutoCloseableCustomArgumentTest {

    private static final AtomicInteger CLOSE_COUNT = new AtomicInteger(0);

    @Paramixel.ArgumentSupplier
    public static Collection<CustomArgument> arguments() {
        Collection<CustomArgument> collection = new ArrayList<>();
        collection.add(new CustomArgument("String 0"));
        return collection;
    }

    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext classContext) {
        CLOSE_COUNT.set(0);
        assertThat(classContext).isNotNull();
        assertThat(classContext.getStore()).isNotNull();
    }

    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(CustomArgument.class);
    }

    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(CustomArgument.class);
    }

    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext classContext) {
        assertThat(classContext).isNotNull();
        assertThat(CLOSE_COUNT.get()).isEqualTo(1);
    }

    public static final class CustomArgument implements Named, AutoCloseable {

        private final String value;

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
