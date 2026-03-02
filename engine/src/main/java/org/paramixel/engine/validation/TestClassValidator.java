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

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Validates Paramixel test classes during discovery.
 *
 * <p>This validator composes multiple validation concerns:
 * <ul>
 *   <li>annotation usage constraints</li>
 *   <li>method signature contracts</li>
 * </ul>
 *
 */
public final class TestClassValidator {

    private TestClassValidator() {}

    /**
     * Validates a test class for Paramixel contract compliance.
     *
     * @param testClass the class to validate
     * @return validation failures; empty when valid
     */
    public static List<ValidationFailure> validateTestClass(final @NonNull Class<?> testClass) {
        final List<ValidationFailure> failures = new ArrayList<>();
        failures.addAll(AnnotationUsageValidator.validateTestClass(testClass));
        failures.addAll(MethodValidator.validateTestClass(testClass));
        return failures;
    }
}
