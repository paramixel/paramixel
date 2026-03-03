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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;

public class TagFilterFactoryTest {

    @Test
    public void fromProperties_emptyProperties_noFilters() {
        Properties props = new Properties();
        TagFilter filter = TagFilterFactory.fromProperties(props);

        assertThat(filter.hasIncludePatterns()).isFalse();
        assertThat(filter.matches(TestClassWithTags.class)).isTrue();
    }

    @Test
    public void fromProperties_withIncludePattern() {
        Properties props = new Properties();
        props.setProperty("paramixel.tags.include", "integration.*");
        TagFilter filter = TagFilterFactory.fromProperties(props);

        assertThat(filter.hasIncludePatterns()).isTrue();
        assertThat(filter.matches(IntegrationTest.class)).isTrue();
        assertThat(filter.matches(UnitTest.class)).isFalse();
    }

    @Test
    public void fromProperties_withExcludePattern() {
        Properties props = new Properties();
        props.setProperty("paramixel.tags.exclude", ".*slow.*");
        TagFilter filter = TagFilterFactory.fromProperties(props);

        assertThat(filter.hasIncludePatterns()).isFalse();
        assertThat(filter.matches(SlowTest.class)).isFalse();
        assertThat(filter.matches(UnitTest.class)).isTrue();
    }

    @Test
    public void fromProperties_withBothPatterns() {
        Properties props = new Properties();
        props.setProperty("paramixel.tags.include", "integration.*");
        props.setProperty("paramixel.tags.exclude", ".*slow.*");
        TagFilter filter = TagFilterFactory.fromProperties(props);

        assertThat(filter.matches(IntegrationSlowTest.class)).isFalse();
        assertThat(filter.matches(IntegrationFastTest.class)).isTrue();
        assertThat(filter.matches(UnitTest.class)).isFalse();
    }

    @Test
    public void fromProperties_commaSeparatedPatterns() {
        Properties props = new Properties();
        props.setProperty("paramixel.tags.include", "^unit$,^fast$");
        TagFilter filter = TagFilterFactory.fromProperties(props);

        assertThat(filter.matches(UnitTest.class)).isTrue();
        assertThat(filter.matches(FastTest.class)).isTrue();
        assertThat(filter.matches(SlowTest.class)).isFalse();
    }

    @Test
    public void fromPatternStrings_withPatterns() {
        TagFilter filter = TagFilterFactory.fromPatternStrings("integration.*", ".*slow.*");

        assertThat(filter.hasIncludePatterns()).isTrue();
        assertThat(filter.matches(IntegrationSlowTest.class)).isFalse();
        assertThat(filter.matches(IntegrationFastTest.class)).isTrue();
    }

    @Test
    public void fromPatternStrings_nullPatterns() {
        TagFilter filter = TagFilterFactory.fromPatternStrings(null, null);

        assertThat(filter.hasIncludePatterns()).isFalse();
        assertThat(filter.matches(TestClassWithTags.class)).isTrue();
    }

    // Test classes

    @Paramixel.TestClass
    @Paramixel.Tags({"test"})
    static class TestClassWithTags {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"integration-database"})
    static class IntegrationTest {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"unit"})
    static class UnitTest {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"slow"})
    static class SlowTest {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"fast"})
    static class FastTest {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"integration-database", "slow"})
    static class IntegrationSlowTest {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"integration-api", "fast"})
    static class IntegrationFastTest {

        @Paramixel.Test
        public void test(final ArgumentContext context) {}
    }
}
