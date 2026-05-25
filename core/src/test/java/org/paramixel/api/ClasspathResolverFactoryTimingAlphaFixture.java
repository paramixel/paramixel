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

package org.paramixel.api;

import org.paramixel.api.action.Action;
import org.paramixel.api.action.Step;

/**
 * Fixture with {@code @Paramixel.Priority(-1)} that records factory-creation and execution timing
 * into {@link ClasspathResolverFactoryTimingLog} to verify that factory invocation respects priority order.
 */
@Paramixel.Priority(-1)
public final class ClasspathResolverFactoryTimingAlphaFixture {

    private ClasspathResolverFactoryTimingAlphaFixture() {}

    /**
     * Creates a step that logs its factory invocation and execution, then returns a
     * {@code "alpha-action"} step.
     *
     * @return a {@link Step} that records entries in {@link ClasspathResolverFactoryTimingLog} on
     *     creation and execution
     */
    @Paramixel.Factory
    public static Action<?> factory() {
        ClasspathResolverFactoryTimingLog.FACTORY_LOG.add("alpha-factory");
        return Step.of("alpha-action", context -> ClasspathResolverFactoryTimingLog.RUN_LOG.add("alpha-run"));
    }
}
