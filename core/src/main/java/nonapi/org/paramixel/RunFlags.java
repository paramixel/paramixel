/*
 * Copyright (c) 2026-present Douglas Hoard
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

package nonapi.org.paramixel;

import org.paramixel.api.Configuration;
import org.paramixel.api.exception.ConfigurationException;

/**
 * Validated boolean settings shared by the runner, scheduler, and result projection.
 *
 * @param failureOnSkip whether a skipped run has a nonzero exit code
 * @param failureOnAbort whether an aborted run has a nonzero exit code
 * @param failIfNoTests whether empty discovery has a nonzero exit code
 * @param failFast whether a failed factory stops later factory admissions
 */
public record RunFlags(boolean failureOnSkip, boolean failureOnAbort, boolean failIfNoTests, boolean failFast) {

    /**
     * Reads the four flags from the effective configuration.
     *
     * @param configuration the effective configuration
     * @return the validated flags
     * @throws ConfigurationException if a present value is neither true nor false
     */
    public static RunFlags from(final Configuration configuration) {
        return new RunFlags(
                read(configuration, Configuration.FAILURE_ON_SKIP, true),
                read(configuration, Configuration.FAILURE_ON_ABORT, true),
                read(configuration, Configuration.FAIL_IF_NO_TESTS, true),
                read(configuration, Configuration.FAIL_FAST, false));
    }

    private static boolean read(final Configuration configuration, final String key, final boolean defaultValue) {
        return configuration
                .getString(key)
                .map(value -> {
                    final var text = value.strip();
                    if ("true".equalsIgnoreCase(text)) {
                        return true;
                    }
                    if ("false".equalsIgnoreCase(text)) {
                        return false;
                    }
                    throw new ConfigurationException("Invalid configuration for '" + key + "': expected true or false");
                })
                .orElse(defaultValue);
    }
}
