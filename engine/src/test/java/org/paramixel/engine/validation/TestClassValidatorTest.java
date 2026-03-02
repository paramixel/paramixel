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

package org.paramixel.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;

public class TestClassValidatorTest {

    @Test
    public void composesUsageAndSignatureValidation() {
        final List<String> messages = TestClassValidator.validateTestClass(BadClass.class).stream()
                .map(ValidationFailure::getMessage)
                .collect(Collectors.toList());

        assertThat(messages)
                .anySatisfy(m -> assertThat(m).contains("@DisplayName value must be non-blank"))
                .anySatisfy(
                        m -> assertThat(m).contains("@Paramixel.Test method").contains("must return void"));
    }

    @Paramixel.DisplayName("  ")
    static class BadClass {

        @Paramixel.Test
        public int test(final ArgumentContext context) {
            return 1;
        }
    }
}
