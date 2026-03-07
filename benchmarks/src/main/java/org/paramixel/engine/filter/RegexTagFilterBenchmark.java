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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.util.EngineConfigurationUtil;

/**
 * JMH benchmarks for {@link RegexTagFilter} tag filtering operations.
 *
 * <p>Benchmarks measure the performance of tag pattern matching with various
 * configurations including include/exclude patterns and class hierarchies.</p>
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class RegexTagFilterBenchmark {

    /**
     * Creates a new benchmark instance.
     */
    public RegexTagFilterBenchmark() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Stores the includeOnlyFilter.
     */
    private RegexTagFilter includeOnlyFilter;
    /**
     * Stores the excludeOnlyFilter.
     */
    private RegexTagFilter excludeOnlyFilter;
    /**
     * Stores the includeExcludeFilter.
     */
    private RegexTagFilter includeExcludeFilter;
    /**
     * Stores the complexPatternsFilter.
     */
    private RegexTagFilter complexPatternsFilter;
    /**
     * Stores the noPatternsFilter.
     */
    private RegexTagFilter noPatternsFilter;

    /**
     * Include patterns used for benchmark scenarios.
     *
     * <p>Values: {@code integration-.*}, {@code ^unit$}.
     */
    private static final List<String> INCLUDE_PATTERNS = Arrays.asList("integration-.*", "^unit$");
    /**
     * Exclude patterns used for benchmark scenarios.
     *
     * <p>Values: {@code .*slow.*}, {@code .*flaky.*}.
     */
    private static final List<String> EXCLUDE_PATTERNS = Arrays.asList(".*slow.*", ".*flaky.*");

    /**
     * More complex include patterns used for benchmark scenarios.
     *
     * <p>Values: {@code ^api-v\d+\..*}, {@code integration-(kafka|mongo).*}, {@code .*\-v\d+$}.
     */
    private static final List<String> COMPLEX_PATTERNS =
            Arrays.asList("^api-v\\d+\\..*", "integration-(kafka|mongo).*", ".*\\-v\\d+$");

    /**
     * Test class with single tag.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     */
    @Paramixel.TestClass
    @Paramixel.Tags({"unit"})
    public static class UnitTest {

        /**
         * Creates a new test class instance.
         */
        public UnitTest() {
            // INTENTIONALLY EMPTY
        }
    }

    /**
     * Test class with integration tag.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     */
    @Paramixel.TestClass
    @Paramixel.Tags({"integration-database"})
    public static class IntegrationDatabaseTest {

        /**
         * Creates a new test class instance.
         */
        public IntegrationDatabaseTest() {
            // INTENTIONALLY EMPTY
        }
    }

    /**
     * Test class with multiple tags.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     */
    @Paramixel.TestClass
    @Paramixel.Tags({"integration-api", "fast"})
    public static class IntegrationApiTest {

        /**
         * Creates a new test class instance.
         */
        public IntegrationApiTest() {
            // INTENTIONALLY EMPTY
        }
    }

    /**
     * Test class with slow tag.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     */
    @Paramixel.TestClass
    @Paramixel.Tags({"integration-kafka", "slow"})
    public static class SlowIntegrationTest {

        /**
         * Creates a new test class instance.
         */
        public SlowIntegrationTest() {
            // INTENTIONALLY EMPTY
        }
    }

    /**
     * Test class with complex versioned tag.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     */
    @Paramixel.TestClass
    @Paramixel.Tags({"api-v2.0", "integration-mongo"})
    public static class VersionedApiTest {

        /**
         * Creates a new test class instance.
         */
        public VersionedApiTest() {
            // INTENTIONALLY EMPTY
        }
    }

    /**
     * Base class with tags.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     */
    @Paramixel.TestClass
    @Paramixel.Tags({"base-integration"})
    public static class BaseTest {

        /**
         * Creates a new test class instance.
         */
        public BaseTest() {
            // INTENTIONALLY EMPTY
        }
    }

    /**
     * Child class that inherits tags.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     */
    @Paramixel.TestClass
    @Paramixel.Tags({"child-fast"})
    public static class ChildTest extends BaseTest {

        /**
         * Creates a new test class instance.
         */
        public ChildTest() {
            // INTENTIONALLY EMPTY
        }
    }

    /**
     * Test class with no tags.
     *
     * @author Douglas Hoard (doug.hoard@gmail.com)
     */
    @Paramixel.TestClass
    public static class UntaggedTest {

        /**
         * Creates a new test class instance.
         */
        public UntaggedTest() {
            // INTENTIONALLY EMPTY
        }
    }

    /**
     * Sets up filters before each benchmark iteration.
     */
    @Setup
    public void setup() {
        includeOnlyFilter = new RegexTagFilter(
                INCLUDE_PATTERNS,
                Collections.<String>emptyList(),
                EngineConfigurationUtil.Source.PROGRAMMATIC,
                EngineConfigurationUtil.Source.PROGRAMMATIC);
        excludeOnlyFilter = new RegexTagFilter(
                Collections.<String>emptyList(),
                EXCLUDE_PATTERNS,
                EngineConfigurationUtil.Source.PROGRAMMATIC,
                EngineConfigurationUtil.Source.PROGRAMMATIC);
        includeExcludeFilter = new RegexTagFilter(
                INCLUDE_PATTERNS,
                EXCLUDE_PATTERNS,
                EngineConfigurationUtil.Source.PROGRAMMATIC,
                EngineConfigurationUtil.Source.PROGRAMMATIC);
        complexPatternsFilter = new RegexTagFilter(
                COMPLEX_PATTERNS,
                Collections.<String>emptyList(),
                EngineConfigurationUtil.Source.PROGRAMMATIC,
                EngineConfigurationUtil.Source.PROGRAMMATIC);
        noPatternsFilter = new RegexTagFilter(
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                EngineConfigurationUtil.Source.PROGRAMMATIC,
                EngineConfigurationUtil.Source.PROGRAMMATIC);
    }

    /**
     * Benchmarks include-only filter with matching class.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void includeOnlyMatch(final Blackhole blackhole) {
        final boolean matches = includeOnlyFilter.matches(IntegrationDatabaseTest.class);
        blackhole.consume(matches);
    }

    /**
     * Benchmarks include-only filter with non-matching class.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void includeOnlyNoMatch(final Blackhole blackhole) {
        final boolean matches = includeOnlyFilter.matches(UntaggedTest.class);
        blackhole.consume(matches);
    }

    /**
     * Benchmarks exclude-only filter with matching class.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void excludeOnlyMatch(final Blackhole blackhole) {
        final boolean matches = excludeOnlyFilter.matches(SlowIntegrationTest.class);
        blackhole.consume(matches);
    }

    /**
     * Benchmarks exclude-only filter with non-matching class.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void excludeOnlyNoMatch(final Blackhole blackhole) {
        final boolean matches = excludeOnlyFilter.matches(UnitTest.class);
        blackhole.consume(matches);
    }

    /**
     * Benchmarks combined include/exclude filter.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void includeExcludeFilter(final Blackhole blackhole) {
        final boolean matches = includeExcludeFilter.matches(IntegrationApiTest.class);
        blackhole.consume(matches);
    }

    /**
     * Benchmarks complex regex patterns.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void complexPatternsMatch(final Blackhole blackhole) {
        final boolean matches = complexPatternsFilter.matches(VersionedApiTest.class);
        blackhole.consume(matches);
    }

    /**
     * Benchmarks tag inheritance from parent class.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void inheritedTagsMatch(final Blackhole blackhole) {
        final boolean matches = includeOnlyFilter.matches(ChildTest.class);
        blackhole.consume(matches);
    }

    /**
     * Benchmarks no patterns filter (pass-through).
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void noPatternsFilter(final Blackhole blackhole) {
        final boolean matches = noPatternsFilter.matches(UnitTest.class);
        blackhole.consume(matches);
    }

    /**
     * Benchmarks filter with multiple test classes.
     *
     * @param blackhole JMH blackhole to prevent dead code elimination
     */
    @Benchmark
    public void filterMultipleClasses(final Blackhole blackhole) {
        blackhole.consume(includeExcludeFilter.matches(UnitTest.class));
        blackhole.consume(includeExcludeFilter.matches(IntegrationDatabaseTest.class));
        blackhole.consume(includeExcludeFilter.matches(IntegrationApiTest.class));
        blackhole.consume(includeExcludeFilter.matches(SlowIntegrationTest.class));
        blackhole.consume(includeExcludeFilter.matches(VersionedApiTest.class));
    }
}
