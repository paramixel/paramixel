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

package org.paramixel.engine.discovery;

import java.util.List;
import java.util.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.validation.TestClassValidator;
import org.paramixel.engine.validation.ValidationFailure;

/**
 * Validates Paramixel test classes.
 *
 * <p>This class handles validation of @TestClass annotated classes,
 * checking for proper method signatures and annotation usage.
 *
 * <p><b>Thread safety</b>
 * <p>This type is stateless. It uses a JVM logger for diagnostics.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class TestValidator {

    /**
     * Logger for validation events.
     */
    private static final Logger LOGGER = Logger.getLogger(TestValidator.class.getName());

    /**
     * Creates a new validator instance.
     */
    public TestValidator() {
        // INTENTIONALLY EMPTY - stateless utility
    }

    /**
     * Validates a test class and returns any validation failures.
     *
     * @param testClass the class to validate
     * @return list of validation failures; empty if valid
     */
    public List<ValidationFailure> validateTestClass(final @NonNull Class<?> testClass) {
        if (isDisabled(testClass)) {
            final Paramixel.Disabled disabled = testClass.getAnnotation(Paramixel.Disabled.class);
            LOGGER.fine("Skipping disabled test class: " + testClass.getName()
                    + (disabled.value().isEmpty() ? "" : " - " + disabled.value()));
            return List.of();
        }

        return TestClassValidator.validateTestClass(testClass);
    }

    /**
     * Checks if a class is disabled.
     *
     * @param testClass the class to check
     * @return true if the class has @Paramixel.Disabled annotation
     */
    private boolean isDisabled(final @NonNull Class<?> testClass) {
        return testClass.isAnnotationPresent(Paramixel.Disabled.class);
    }
}
