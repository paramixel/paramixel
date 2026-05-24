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

package org.paramixel.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;
import org.paramixel.api.action.Action;
import org.paramixel.api.action.Parallel;
import org.paramixel.api.exception.ResolverException;
import org.paramixel.api.selector.Selector;

@DisplayName("ClasspathResolver arguments")
class ClasspathResolverArgumentsTest {

    @Test
    @DisplayName("rejects null configuration")
    void rejectsNullConfiguration() {
        assertThatThrownBy(() -> new ClasspathResolver(null, Selector.all()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("configuration must not be null");
    }

    @Test
    @DisplayName("rejects null selector")
    void rejectsNullSelector() {
        assertThatThrownBy(() -> new ClasspathResolver(Configuration.defaultConfiguration(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("selector must not be null");
    }

    @Test
    @DisplayName("blank tag values produce discovery validation failure action")
    void rejectsBlankTagValues() {
        Selector selector = Selector.classOf(ClasspathResolverTest.BlankTagFactory.class);

        var configuration = Configuration.defaultConfiguration();
        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Parallel<?> root = (Parallel<?>) result.orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).name()).startsWith("Discovery validation failure:");
    }

    @Test
    @DisplayName("rejects non-public factory method")
    void rejectsNonPublicFactoryMethod() {
        Selector selector = Selector.classOf(ClasspathResolverTest.NonPublicFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ClasspathResolver(configuration, selector).resolveActions())
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("method must be public static");
    }

    @Test
    @DisplayName("tag selector excludes invalid factory before signature validation")
    void tagSelectorExcludesInvalidFactoryBeforeSignatureValidation() {
        Selector selector = Selector.and(
                Selector.classOf(ClasspathResolverTest.NonPublicFactory.class), Selector.tagRegex("^smoke$"));
        var configuration = Configuration.defaultConfiguration();

        assertThat(new ClasspathResolver(configuration, selector).resolveActions())
                .isEmpty();
    }

    @Test
    @DisplayName("rejects non-static factory method")
    void rejectsNonStaticFactoryMethod() {
        Selector selector = Selector.classOf(ClasspathResolverTest.NonStaticFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ClasspathResolver(configuration, selector).resolveActions())
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("method must be public static");
    }

    @Test
    @DisplayName("rejects parameterized factory method")
    void rejectsParameterizedFactoryMethod() {
        Selector selector = Selector.classOf(ClasspathResolverTest.ParameterizedFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ClasspathResolver(configuration, selector).resolveActions())
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("method must have no parameters");
    }

    @Test
    @DisplayName("rejects factory with wrong return type")
    void rejectsFactoryWithWrongReturnType() {
        Selector selector = Selector.classOf(ClasspathResolverTest.WrongReturnTypeFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ClasspathResolver(configuration, selector).resolveActions())
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("return type must be Spec or Action");
    }

    @Test
    @DisplayName("accepts factory that returns null as skipped")
    void acceptsFactoryThatReturnsNullAsSkipped() {
        Selector selector = Selector.classOf(ClasspathResolverTest.NullReturningFactory.class);
        var configuration = Configuration.defaultConfiguration();

        Optional<Action<?>> result = new ClasspathResolver(configuration, selector).resolveActions();

        assertThat(result).isPresent();
        Parallel<?> root = (Parallel<?>) result.orElseThrow();
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).name()).startsWith("Skipped factory:");
    }

    @Test
    @DisplayName("wraps RuntimeException from factory method in ResolverException")
    void wrapsRuntimeExceptionFromFactoryMethodInResolverException() {
        Selector selector = Selector.classOf(ClasspathResolverTest.RuntimeExceptionThrowingFactory.class);
        var configuration = Configuration.defaultConfiguration();

        assertThatThrownBy(() -> new ClasspathResolver(configuration, selector).resolveActions())
                .isInstanceOf(ResolverException.class)
                .hasMessageContaining("Failed to invoke @Paramixel.Factory method")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
