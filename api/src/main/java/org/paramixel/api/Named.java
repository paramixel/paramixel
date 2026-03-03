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

package org.paramixel.api;

/**
 * Provides a display name for test arguments in parameterized tests.
 *
 * <p>This interface allows test arguments to specify a human-readable name that
 * will be used in test reports, IDE test runners, and build tool output. By
 * implementing this interface, complex argument objects can provide meaningful
 * names instead of relying on the default {@link Object#toString()} representation.</p>
 *
 * <p>When an argument implements {@code Named}, the framework uses the value
 * returned by {@link #getName()} as the display name for that specific test
 * invocation. This improves test readability and makes it easier to identify
 * which specific argument combination failed when reviewing test results.</p>
 *
 * <p><b>Implementation Requirements:</b></p>
 * <ul>
 *   <li>The {@code getName()} method must return a non-null string</li>
 *   <li>The returned name should be concise but descriptive</li>
 *   <li>The name should uniquely identify the argument within its set</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * public class TestScenario implements Named {
 *     private final String name;
 *     private final String input;
 *     private final String expectedOutput;
 *
 *     public TestScenario(String name, String input, String expectedOutput) {
 *         this.name = name;
 *         this.input = input;
 *         this.expectedOutput = expectedOutput;
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return name;
 *     }
 *
 *     public String getInput() {
 *         return input;
 *     }
 *
 *     public String getExpectedOutput() {
 *         return expectedOutput;
 *     }
 * }
 *
 * @Paramixel.TestClass
 * public class ParameterizedTests {
 *
 *     @Paramixel.ArgumentsCollector
 *     public static List<TestScenario> provideScenarios() {
 *         return Arrays.asList(
 *             new TestScenario("empty string", "", ""),
 *             new TestScenario("single character", "a", "A"),
 *             new TestScenario("multiple words", "hello world", "HELLO WORLD")
 *         );
 *     }
 *
 *     @Paramixel.Test
 *     public void testUpperCase(ArgumentContext context) {
 *         TestScenario scenario = context.getArgument(TestScenario.class);
 *         String result = scenario.getInput().toUpperCase();
 *         assertEquals(scenario.getExpectedOutput(), result);
 *     }
 * }
 * }</pre>
 *
 * @see Paramixel.ArgumentsCollector
 * @see ArgumentContext
 * @since 0.0.1
 */
public interface Named {

    /**
     * Returns the display name for this test argument.
     *
     * <p>This name is used by the test framework when generating test reports
     * and displaying test results. It should be concise, descriptive, and
     * unique within the set of arguments.</p>
     *
     * @return the display name for this argument; never {@code null}
     */
    String getName();
}
