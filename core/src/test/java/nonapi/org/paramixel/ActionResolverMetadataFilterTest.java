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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import nonapi.org.paramixel.metadatafilter.InvalidNonPublicFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

@DisplayName("ActionResolver metadata filter")
class ActionResolverMetadataFilterTest {

    @Test
    @DisplayName("class filter resolves included fixture and excludes non-matching")
    void classFilterResolvesIncludedFixtureAndExcludesNonMatching() {
        Selector selector = Selector.and(Selector.classRegex("IncludedSmokeFixture$"), Selector.tagRegex("^smoke$"));

        var configuration = Configuration.defaultConfiguration();
        Optional<Action> result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isInstanceOf(Parallel.class);
    }

    @Test
    @DisplayName("package filter excludes metadatafilter package")
    void packageFilterExcludesMetadataFilterPackage() {
        Selector selector =
                Selector.and(Selector.packageRegex("^org\\.paramixel\\.api$"), Selector.tagRegex("^smoke$"));

        var configuration = Configuration.defaultConfiguration();
        Optional<Action> result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("tag filter excludes non-matching fixture")
    void tagFilterExcludesNonMatchingFixture() {
        Selector selector = Selector.and(Selector.classRegex("ExcludedByTagFilter"), Selector.tagRegex("^smoke$"));

        var configuration = Configuration.defaultConfiguration();
        Optional<Action> result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("tag filter includes matching fixture")
    void tagFilterIncludesMatchingFixture() {
        Selector selector = Selector.and(Selector.classRegex("IncludedSmokeFixture"), Selector.tagRegex("^smoke$"));

        var configuration = Configuration.defaultConfiguration();
        Optional<Action> result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("disabled-only fixture is not resolved")
    void disabledOnlyFixtureIsNotResolved() {
        Selector selector = Selector.classRegex("DisabledOnlyFixture");

        var configuration = Configuration.defaultConfiguration();
        Optional<Action> result = new ActionResolver(configuration, selector).resolveRootAction();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("non-public factory that passes metadata filter still throws ResolverException")
    void nonPublicFactoryThatPassesMetadataFilterStillThrows() {
        Selector selector = Selector.classOf(InvalidNonPublicFixture.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ActionResolver(configuration, selector).resolveRootAction())
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("method must be public static");
    }
}
