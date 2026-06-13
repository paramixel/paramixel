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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import nonapi.org.paramixel.ActionResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.selector.Selector;

@DisplayName("ActionResolver discovery order")
class ActionResolverDiscoveryOrderTest {

    @BeforeEach
    void clearFactoryTimingLog() {
        ActionResolverFactoryTimingLog.clear();
    }

    @Test
    @DisplayName("action name is not used as a discovery sort key")
    void actionNameIsNotUsedAsDiscoverySortKey() {
        var selector = Selector.classRegex("ActionResolverMetadata");
        var configuration = Configuration.defaultConfiguration();
        var result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        assertThat(((Parallel) result.orElseThrow()).children())
                .extracting(Action::displayName)
                .containsExactly("z-action", "a-action");
    }

    @Test
    @DisplayName("factories are invoked after metadata sorting")
    void factoriesAreInvokedAfterMetadataSorting() {
        var selector = Selector.classRegex("ActionResolverFactoryTiming");
        var configuration = Configuration.defaultConfiguration();
        new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(ActionResolverFactoryTimingLog.FACTORY_LOG).containsExactly("zeta-factory", "alpha-factory");
    }

    @Test
    @DisplayName("step run contains actual work and is not executed during discovery")
    void stepRunContainsActualWorkAndIsNotExecutedDuringDiscovery() {
        var selector = Selector.classRegex("ActionResolverFactoryTiming");
        var configuration = Configuration.defaultConfiguration();
        var root = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(ActionResolverFactoryTimingLog.FACTORY_LOG).containsExactly("zeta-factory", "alpha-factory");
        assertThat(ActionResolverFactoryTimingLog.RUN_LOG).isEmpty();

        var runner = Runner.builder()
                .configuration(Configuration.of(Map.of(Configuration.RUNNER_PARALLELISM, "1")))
                .listener(new Listener() {})
                .build();
        runner.run(root.orElseThrow());

        assertThat(ActionResolverFactoryTimingLog.RUN_LOG).containsExactlyInAnyOrder("zeta-run", "alpha-run");
    }
}
