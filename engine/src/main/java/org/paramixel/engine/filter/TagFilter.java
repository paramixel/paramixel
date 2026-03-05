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

package org.paramixel.engine.filter;

import org.jspecify.annotations.NonNull;

/**
 * Interface for filtering test classes based on tags.
 *
 * <p>Implementations determine whether a test class should be included in the test run
 * based on the tags applied to it via {@code @Paramixel.Tags}.</p>
 *
 * @since 0.0.1
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public interface TagFilter {

    /**
     * Determines if the given test class should be included.
     *
     * <p>The class is checked against include and exclude patterns. Classes without
     * {@code @Paramixel.Tags} are only included if no include patterns are specified.</p>
     *
     * @param testClass the test class to filter; never {@code null}
     * @return {@code true} if the class should be included, {@code false} to exclude it
     * @since 0.0.1
     */
    boolean matches(final @NonNull Class<?> testClass);

    /**
     * Determines if this filter has any include patterns configured.
     *
     * @return {@code true} if include patterns are configured, {@code false} otherwise
     * @since 0.0.1
     */
    boolean hasIncludePatterns();
}
