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

import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class NullArgumentTest {

    private static final AtomicBoolean TEST_EXECUTED = new AtomicBoolean(false);

    @Paramixel.ArgumentSupplier
    public static Object arguments() {
        return null;
    }

    @Paramixel.Test
    public void test(final @NonNull ArgumentContext argumentContext) {
        TEST_EXECUTED.set(true);
        throw new IllegalStateException("Should not be executed");
    }

    @Paramixel.Finalize
    public void finalize(final @NonNull ClassContext classContext) {
        assertThat(classContext).isNotNull();
        assertThat(TEST_EXECUTED.get()).isFalse();
    }
}
