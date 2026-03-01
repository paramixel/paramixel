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

package test.lifecycle;

import java.util.Collection;
import java.util.Collections;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class LifecycleInheritanceTest extends LifecycleInheritanceBase {

    @Paramixel.ArgumentSupplier
    public static Collection<String> arguments() {
        return Collections.singletonList("argument[0]");
    }

    @Paramixel.Initialize
    public void subClassInitialize(final ClassContext classContext) {
        actual.add("subClassInitialize");
    }

    @Paramixel.BeforeAll
    public void subClassBeforeAll(final @NonNull ArgumentContext argumentContext) {
        actual.add("subClassBeforeAll");
    }

    @Paramixel.BeforeEach
    public void subClassBeforeEach(final @NonNull ArgumentContext argumentContext) {
        actual.add("subClassBeforeEach");
    }

    @Paramixel.Test
    public void test1(final @NonNull ArgumentContext argumentContext) {
        actual.add("test1");
    }

    @Paramixel.Test
    public void test2(final @NonNull ArgumentContext argumentContext) {
        actual.add("test2");
    }

    @Paramixel.AfterEach
    public void subClassAfterEach(final @NonNull ArgumentContext argumentContext) {
        actual.add("subClassAfterEach");
    }

    @Paramixel.AfterAll
    public void subClassAfterAll(final @NonNull ArgumentContext argumentContext) {
        actual.add("subClassAfterAll");
    }

    @Paramixel.Finalize
    public void subClassFinalize(final ClassContext classContext) {
        actual.add("subClassFinalize");
    }
}
