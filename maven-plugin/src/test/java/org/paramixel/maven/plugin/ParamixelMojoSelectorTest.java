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

package org.paramixel.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import nonapi.org.paramixel.selector.ConcreteSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.Configuration;

@DisplayName("ParamixelMojo selection")
class ParamixelMojoSelectorTest {

    @Test
    @DisplayName("buildSelector resolves selection from the effective configuration")
    void buildSelectorResolvesFromConfiguration() {
        var mojo = new ParamixelMojo();

        var selector = mojo.buildSelector();

        assertThat(ConcreteSelector.require(selector).usesConfiguration()).isTrue();
    }

    @Test
    @DisplayName("no match params leave the configuration free of match keys, so everything is selected")
    void noMatchParamsSelectEverything() throws Exception {
        var mojo = new ParamixelMojo();

        var configuration = buildConfiguration(mojo);
        var selector = ConcreteSelector.require(mojo.buildSelector()).resolve(configuration);

        assertThat(configuration.getString(Configuration.MATCH_PACKAGE_REGEX)).isEmpty();
        assertThat(configuration.getString(Configuration.MATCH_CLASS_REGEX)).isEmpty();
        assertThat(configuration.getString(Configuration.MATCH_TAG_REGEX)).isEmpty();
        assertThat(selector.acceptsClass("any.pkg", "any.pkg.AnyClass")).isTrue();
    }

    @Test
    @DisplayName("matchPackage is applied as a package pattern")
    void matchPackageIsApplied() throws Exception {
        var mojo = new ParamixelMojo();
        setField(mojo, "matchPackage", ".*fixtures.*");

        var configuration = buildConfiguration(mojo);
        var selector = ConcreteSelector.require(mojo.buildSelector()).resolve(configuration);

        assertThat(configuration.getString(Configuration.MATCH_PACKAGE_REGEX)).contains(".*fixtures.*");
        assertThat(selector.acceptsClass("a.fixtures", "a.fixtures.Fixture")).isTrue();
        assertThat(selector.acceptsClass("a.other", "a.other.Fixture")).isFalse();
    }

    @Test
    @DisplayName("matchTag is applied as a tag pattern")
    void matchTagIsApplied() throws Exception {
        var mojo = new ParamixelMojo();
        setField(mojo, "matchTag", "smoke");

        var configuration = buildConfiguration(mojo);
        var selector = ConcreteSelector.require(mojo.buildSelector()).resolve(configuration);

        assertThat(configuration.getString(Configuration.MATCH_TAG_REGEX)).contains("smoke");
        assertThat(selector.test("p", "p.C", List.of("smoke"))).isTrue();
        assertThat(selector.test("p", "p.C", List.of("slow"))).isFalse();
    }

    @Test
    @DisplayName("matchClass is applied as a class pattern")
    void matchClassIsApplied() throws Exception {
        var mojo = new ParamixelMojo();
        setField(mojo, "matchClass", ".*Fixture.*");

        var configuration = buildConfiguration(mojo);
        var selector = ConcreteSelector.require(mojo.buildSelector()).resolve(configuration);

        assertThat(configuration.getString(Configuration.MATCH_CLASS_REGEX)).contains(".*Fixture.*");
        assertThat(selector.acceptsClass("p", "p.MyFixture")).isTrue();
        assertThat(selector.acceptsClass("p", "p.Other")).isFalse();
    }

    @Test
    @DisplayName("multiple match params are combined")
    void multipleMatchParamsAreCombined() throws Exception {
        var mojo = new ParamixelMojo();
        setField(mojo, "matchClass", ".*Mojo.*");
        setField(mojo, "matchTag", "smoke");

        var configuration = buildConfiguration(mojo);
        var selector = ConcreteSelector.require(mojo.buildSelector()).resolve(configuration);

        assertThat(selector.test("p", "p.MyMojo", List.of("smoke"))).isTrue();
        assertThat(selector.test("p", "p.MyMojo", List.of("slow"))).isFalse();
        assertThat(selector.test("p", "p.Other", List.of("smoke"))).isFalse();
    }

    @Test
    @DisplayName("ignores blank matchClass")
    void ignoresBlankMatchClass() throws Exception {
        var mojo = new ParamixelMojo();
        setField(mojo, "matchClass", "   ");

        assertThat(buildConfiguration(mojo).getString(Configuration.MATCH_CLASS_REGEX))
                .isEmpty();
    }

    @Test
    @DisplayName("ignores blank matchPackage")
    void ignoresBlankMatchPackage() throws Exception {
        var mojo = new ParamixelMojo();
        setField(mojo, "matchPackage", "   ");

        assertThat(buildConfiguration(mojo).getString(Configuration.MATCH_PACKAGE_REGEX))
                .isEmpty();
    }

    @Test
    @DisplayName("ignores blank matchTag")
    void ignoresBlankMatchTag() throws Exception {
        var mojo = new ParamixelMojo();
        setField(mojo, "matchTag", "   ");

        assertThat(buildConfiguration(mojo).getString(Configuration.MATCH_TAG_REGEX))
                .isEmpty();
    }

    private static Configuration buildConfiguration(final ParamixelMojo mojo) throws Exception {
        var method = ParamixelMojo.class.getDeclaredMethod("buildConfiguration", ClassLoader.class);
        method.setAccessible(true);
        return (Configuration) method.invoke(mojo, Thread.currentThread().getContextClassLoader());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var clazz = target.getClass();
        Field field = null;
        while (clazz != null && field == null) {
            try {
                field = clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        field.set(target, value);
    }
}
