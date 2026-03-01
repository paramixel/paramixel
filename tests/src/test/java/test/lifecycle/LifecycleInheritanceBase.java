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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

public abstract class LifecycleInheritanceBase {

    protected static final List<String> actual = Collections.synchronizedList(new ArrayList<>());

    @Paramixel.Initialize
    public void baseClassInitialize(final ClassContext classContext) {
        actual.clear();
        actual.add("baseClassInitialize");
    }

    @Paramixel.BeforeAll
    public void baseClassBeforeAll(final @NonNull ArgumentContext argumentContext) {
        actual.add("baseClassBeforeAll");
    }

    @Paramixel.BeforeEach
    public void baseClassBeforeEach(final @NonNull ArgumentContext argumentContext) {
        actual.add("baseClassBeforeEach");
    }

    @Paramixel.AfterEach
    public void baseClassAfterEach(final @NonNull ArgumentContext argumentContext) {
        actual.add("baseClassAfterEach");
    }

    @Paramixel.AfterAll
    public void baseClassAfterAll(final @NonNull ArgumentContext argumentContext) {
        actual.add("baseClassAfterAll");
    }

    @Paramixel.Finalize
    public void baseClassFinalize(final ClassContext classContext) {
        actual.add("baseClassFinalize");

        List<String> expected = new ArrayList<>();

        expected.add("baseClassInitialize");
        expected.add("subClassInitialize");
        expected.add("baseClassBeforeAll");
        expected.add("subClassBeforeAll");
        expected.add("baseClassBeforeEach");
        expected.add("subClassBeforeEach");
        expected.add("test1");
        expected.add("subClassAfterEach");
        expected.add("baseClassAfterEach");
        expected.add("baseClassBeforeEach");
        expected.add("subClassBeforeEach");
        expected.add("test2");
        expected.add("subClassAfterEach");
        expected.add("baseClassAfterEach");
        expected.add("subClassAfterAll");
        expected.add("baseClassAfterAll");
        expected.add("subClassFinalize");
        expected.add("baseClassFinalize");

        assertThat(actual).containsExactlyElementsOf(expected);
    }
}
