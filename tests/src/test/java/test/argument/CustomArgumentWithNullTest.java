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
import org.paramixel.api.ClassContext;
import org.paramixel.api.Named;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class CustomArgumentWithNullTest {

    @Paramixel.ArgumentSupplier(parallelism = 2)
    public static CustomArgument[] arguments() {
        CustomArgument[] customArguments = new CustomArgument[10];

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                customArguments[i] = null;
            } else {
                customArguments[i] = new CustomArgument("String " + i);
            }
        }

        return customArguments;
    }

    @Paramixel.Initialize
    public void initialize(final @NonNull ClassContext classContext) {
        assertThat(classContext).isNotNull();
        assertThat(classContext.getStore()).isNotNull();
    }

    @Paramixel.BeforeAll
    public void beforeAll(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();
    }

    @Paramixel.BeforeEach
    public void beforeEach(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();
    }

    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();

        Object argument = argumentContext.getArgument();
        if (argument == null) {
            return;
        }

        assertThat(argument).isInstanceOf(CustomArgument.class);
        assertThat(((CustomArgument) argument).getName()).startsWith("CustomArgument(");
    }

    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();

        Object argument = argumentContext.getArgument();
        if (argument == null) {
            return;
        }

        assertThat(argument).isInstanceOf(CustomArgument.class);
        assertThat(argument).isInstanceOf(Named.class);
    }

    @Paramixel.AfterEach
    public void afterEach(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();
    }

    @Paramixel.AfterAll
    public void afterAll(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();
    }

    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext classContext) {
        assertThat(classContext).isNotNull();
        assertThat(classContext.getStore()).isNotNull();
    }

    public static final class CustomArgument implements Named {

        private final String value;

        public CustomArgument(final @NonNull String value) {
            this.value = value;
        }

        @Override
        public String getName() {
            return "CustomArgument(" + value + ")";
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
