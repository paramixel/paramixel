/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
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

package test.named;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.NamedValue;
import org.paramixel.api.Paramixel;

/**
 * Confirms {@link NamedValue} supports null payloads and keeps the provided name.
 */
@Paramixel.TestClass
public class NamedValueTest3 {

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArgument(NamedValue.of("null payload", null));
    }

    @Paramixel.Test
    public void permitsNullValue(final @NonNull ArgumentContext context) {
        NamedValue<?> argument = context.getArgument(NamedValue.class);

        assertThat(argument.getName()).isEqualTo("null payload");
        assertThat(argument.getValue()).isNull();
        assertThat(argument.getValue(Object.class)).isNull();
    }
}
