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

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;

public class RegexTagFilterTest {

    @Test
    public void matches_noFilters_includesAllClasses() {
        RegexTagFilter filter = new RegexTagFilter(List.of(), List.of());

        assertThat(filter.matches(IntegrationDatabaseTest.class)).isTrue();
        assertThat(filter.matches(UnitFastTest.class)).isTrue();
        assertThat(filter.matches(UntaggedTest.class)).isTrue();
    }

    @Test
    public void matches_includePattern_matchesClassWithTag() {
        RegexTagFilter filter = new RegexTagFilter(List.of("integration-.*"), List.of());

        assertThat(filter.matches(IntegrationDatabaseTest.class)).isTrue();
        assertThat(filter.matches(IntegrationApiTest.class)).isTrue();
        assertThat(filter.matches(UnitFastTest.class)).isFalse();
        assertThat(filter.matches(UntaggedTest.class)).isFalse();
    }

    @Test
    public void matches_includePattern_exactMatch() {
        RegexTagFilter filter = new RegexTagFilter(List.of("^unit$"), List.of());

        assertThat(filter.matches(UnitFastTest.class)).isTrue();
        assertThat(filter.matches(IntegrationDatabaseTest.class)).isFalse();
        assertThat(filter.matches(UntaggedTest.class)).isFalse();
    }

    @Test
    public void matches_includePattern_multiplePatterns() {
        RegexTagFilter filter = new RegexTagFilter(List.of("^unit$", "^fast$"), List.of());

        assertThat(filter.matches(UnitFastTest.class)).isTrue();
        assertThat(filter.matches(IntegrationDatabaseTest.class)).isFalse();
        assertThat(filter.matches(IntegrationApiTest.class)).isTrue();
    }

    @Test
    public void matches_excludePattern_excludesMatchingClass() {
        RegexTagFilter filter = new RegexTagFilter(List.of(), List.of(".*slow.*"));

        assertThat(filter.matches(IntegrationDatabaseTest.class)).isFalse();
        assertThat(filter.matches(UnitFastTest.class)).isTrue();
        assertThat(filter.matches(IntegrationApiTest.class)).isTrue();
        assertThat(filter.matches(UntaggedTest.class)).isTrue();
    }

    @Test
    public void matches_includeAndExclude_combined() {
        RegexTagFilter filter = new RegexTagFilter(List.of("integration-.*"), List.of(".*slow.*"));

        assertThat(filter.matches(IntegrationDatabaseTest.class)).isFalse();
        assertThat(filter.matches(IntegrationApiTest.class)).isTrue();
        assertThat(filter.matches(UnitFastTest.class)).isFalse();
    }

    @Test
    public void matches_untaggedClass_notIncludedWhenIncludeFilterPresent() {
        RegexTagFilter filter = new RegexTagFilter(List.of("unit"), List.of());

        assertThat(filter.matches(UntaggedTest.class)).isFalse();
    }

    @Test
    public void matches_untaggedClass_includedWhenNoIncludeFilter() {
        RegexTagFilter filter = new RegexTagFilter(List.of(), List.of("slow"));

        assertThat(filter.matches(UntaggedTest.class)).isTrue();
    }

    @Test
    public void matches_escapedMetacharacters() {
        RegexTagFilter filter = new RegexTagFilter(List.of("v1\\.0"), List.of());

        assertThat(filter.matches(VersionTest.class)).isTrue();
        assertThat(filter.matches(UnitFastTest.class)).isFalse();
    }

    @Test
    public void matches_caseSensitive() {
        RegexTagFilter filter = new RegexTagFilter(List.of("Unit"), List.of());

        assertThat(filter.matches(UnitFastTest.class)).isFalse();
    }

    @Test
    public void hasIncludePatterns_empty_returnsFalse() {
        RegexTagFilter filter = new RegexTagFilter(List.of(), List.of("slow"));
        assertThat(filter.hasIncludePatterns()).isFalse();
    }

    @Test
    public void hasIncludePatterns_withPatterns_returnsTrue() {
        RegexTagFilter filter = new RegexTagFilter(List.of("unit"), List.of());
        assertThat(filter.hasIncludePatterns()).isTrue();
    }

    @Test
    public void invalidRegexPattern_skippedWithWarning() {
        // Invalid pattern "integration[" should be skipped
        RegexTagFilter filter = new RegexTagFilter(List.of("integration[", "unit"), List.of());

        // Should still work with the valid pattern
        assertThat(filter.matches(UnitFastTest.class)).isTrue();
        assertThat(filter.getIncludePatternCount()).isEqualTo(1);
    }

    @Test
    public void emptyPatternString_ignored() {
        RegexTagFilter filter = new RegexTagFilter(List.of("", "  ", "unit"), List.of());

        assertThat(filter.getIncludePatternCount()).isEqualTo(1);
        assertThat(filter.matches(UnitFastTest.class)).isTrue();
    }

    @Test
    public void matches_allTagsChecked() {
        // Class has tags: "integration-database", "slow"
        // Pattern matches "integration-database" which is different from just "database"
        RegexTagFilter filter = new RegexTagFilter(List.of("integration-database"), List.of());

        assertThat(filter.matches(IntegrationDatabaseTest.class)).isTrue();
    }

    @Test
    public void matches_inheritedTags_fromParentClass() {
        // Child class inherits tags from parent class
        // Parent has: "integration", "database"
        // Child has: "fast"
        // Combined: ["integration", "database", "fast"]
        RegexTagFilter filter = new RegexTagFilter(List.of("integration"), List.of());

        // Should match because parent has "integration" tag
        assertThat(filter.matches(ChildTestWithInheritedTags.class)).isTrue();
    }

    @Test
    public void matches_inheritedTags_matchesChildTag() {
        // Child class inherits tags from parent class
        // Parent has: "integration", "database"
        // Child has: "fast"
        // Combined: ["integration", "database", "fast"]
        RegexTagFilter filter = new RegexTagFilter(List.of("fast"), List.of());

        // Should match because child has "fast" tag
        assertThat(filter.matches(ChildTestWithInheritedTags.class)).isTrue();
    }

    @Test
    public void matches_inheritedTags_excludedByParentTag() {
        // Child class inherits tags from parent class
        // Parent has: "integration", "database"
        // Child has: "fast"
        // Combined: ["integration", "database", "fast"]
        RegexTagFilter filter = new RegexTagFilter(List.of(), List.of("slow"));

        // Should match because neither parent nor child has "slow" tag
        assertThat(filter.matches(ChildTestWithInheritedTags.class)).isTrue();

        // Create filter that excludes "database" - should exclude because parent has it
        RegexTagFilter excludeFilter = new RegexTagFilter(List.of(), List.of("database"));
        assertThat(excludeFilter.matches(ChildTestWithInheritedTags.class)).isFalse();
    }

    // Test classes

    @Paramixel.TestClass
    @Paramixel.Tags({"integration-database", "slow"})
    static class IntegrationDatabaseTest {

        @Paramixel.Test
        public void test(final @NonNull ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"unit", "fast"})
    static class UnitFastTest {

        @Paramixel.Test
        public void test(final @NonNull ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"integration-api", "fast"})
    static class IntegrationApiTest {

        @Paramixel.Test
        public void test(final @NonNull ArgumentContext context) {}
    }

    @Paramixel.TestClass
    static class UntaggedTest {

        @Paramixel.Test
        public void test(final @NonNull ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"v1.0", "api"})
    static class VersionTest {

        @Paramixel.Test
        public void test(final @NonNull ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"integration", "database"})
    static class ParentTestWithTags {

        @Paramixel.Test
        public void test(final @NonNull ArgumentContext context) {}
    }

    @Paramixel.TestClass
    @Paramixel.Tags({"fast"})
    static class ChildTestWithInheritedTags extends ParentTestWithTags {

        @Paramixel.Test
        public void test2(final @NonNull ArgumentContext context) {}
    }
}
