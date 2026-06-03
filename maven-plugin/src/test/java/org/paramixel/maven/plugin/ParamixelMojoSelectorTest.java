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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.paramixel.api.selector.Selector;

@DisplayName("ParamixelMojo buildSelector()")
class ParamixelMojoSelectorTest {

    @Test
    @DisplayName("returns Selector.all() when no match params are set")
    void returnsAllWhenNoMatchParamsAreSet() throws Exception {
        ParamixelMojo mojo = new ParamixelMojo();

        Selector selector = mojo.buildSelector();

        assertThat(selector).isNotNull();
        assertThat(selector.toString()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("returns package regex selector when matchPackage is set")
    void returnsPackageRegexSelectorWhenMatchPackageIsSet() throws Exception {
        ParamixelMojo mojo = new ParamixelMojo();
        setField(mojo, "matchPackage", ".*fixtures.*");

        Selector selector = mojo.buildSelector();

        assertThat(selector).isNotNull();
    }

    @Test
    @DisplayName("returns tag regex selector when matchTag is set")
    void returnsTagRegexSelectorWhenMatchTagIsSet() throws Exception {
        ParamixelMojo mojo = new ParamixelMojo();
        setField(mojo, "matchTag", "smoke");

        Selector selector = mojo.buildSelector();

        assertThat(selector).isNotNull();
    }

    @Test
    @DisplayName("returns class regex selector when only matchClass is set (case 1)")
    void returnsClassRegexSelectorWhenOnlyMatchClassIsSet() throws Exception {
        ParamixelMojo mojo = new ParamixelMojo();
        setField(mojo, "matchClass", ".*Fixture.*");

        Selector selector = mojo.buildSelector();

        assertThat(selector).isNotNull();
    }

    @Test
    @DisplayName("returns and() selector when multiple match params are set (default case)")
    void returnsAndSelectorWhenMultipleMatchParamsAreSet() throws Exception {
        ParamixelMojo mojo = new ParamixelMojo();
        setField(mojo, "matchClass", ".*Mojo.*");
        setField(mojo, "matchTag", "smoke");

        Selector selector = mojo.buildSelector();

        assertThat(selector).isNotNull();
    }

    @Test
    @DisplayName("ignores blank matchClass")
    void ignoresBlankMatchClass() throws Exception {
        ParamixelMojo mojo = new ParamixelMojo();
        setField(mojo, "matchClass", "   ");

        Selector selector = mojo.buildSelector();

        assertThat(selector).isNotNull();
    }

    @Test
    @DisplayName("ignores blank matchPackage")
    void ignoresBlankMatchPackage() throws Exception {
        ParamixelMojo mojo = new ParamixelMojo();
        setField(mojo, "matchPackage", "   ");

        Selector selector = mojo.buildSelector();

        assertThat(selector).isNotNull();
    }

    @Test
    @DisplayName("ignores blank matchTag")
    void ignoresBlankMatchTag() throws Exception {
        ParamixelMojo mojo = new ParamixelMojo();
        setField(mojo, "matchTag", "   ");

        Selector selector = mojo.buildSelector();

        assertThat(selector).isNotNull();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> clazz = target.getClass();
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
