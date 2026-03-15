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

package org.paramixel.engine.discovery;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.TestDescriptor;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.filter.TagFilter;
import org.paramixel.engine.filter.TagFilterFactory;
import org.paramixel.engine.util.PropertiesLoaderUtil;
import org.paramixel.engine.validation.ValidationFailure;

/**
 * Orchestrates Paramixel test discovery.
 *
 * <p>This class coordinates the discovery process by delegating to specialized classes:
 * <ul>
 *   <li>{@link ClasspathScanner} - scans classpath for test classes</li>
 *   <li>{@link TestValidator} - validates test classes</li>
 *   <li>{@link DescriptorBuilder} - builds descriptor hierarchy</li>
 * </ul>
 *
 * <p><b>Descriptor structure</b>
 * <pre>
 * engine:paramixel
 *   class:&lt;fqcn&gt;
 *     argument:&lt;index&gt;
 *       method:&lt;name&gt;
 * </pre>
 *
 * <p><b>Thread safety</b>
 * <p>This type is stateless. It uses a JVM logger for diagnostics.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 */
public final class ParamixelDiscovery {

    /**
     * Logger for discovery events.
     */
    private static final Logger LOGGER = Logger.getLogger(ParamixelDiscovery.class.getName());

    /**
     * Unique ID segment for the engine root.
     */
    private static final String ENGINE_ID_SEGMENT = "paramixel";

    /**
     * Minimal engine context used during discovery-time operations.
     */
    private static final ConcreteEngineContext DISCOVERY_ENGINE_CONTEXT =
            new ConcreteEngineContext(ENGINE_ID_SEGMENT, new Properties(), 1);

    /**
     * Creates a new discovery instance.
     */
    public ParamixelDiscovery() {
        // INTENTIONALLY EMPTY
    }

    /**
     * Performs test discovery.
     *
     * @param request the discovery request
     * @param engineDescriptor the engine descriptor
     */
    public void discoverTests(
            final @NonNull EngineDiscoveryRequest request, final @NonNull TestDescriptor engineDescriptor) {
        LOGGER.fine("Starting Paramixel test discovery");

        // Validate tag filter configuration up-front (invalid regex patterns must fail discovery).
        final var rawProperties =
                PropertiesLoaderUtil.loadProjectRootPropertiesOrFail("paramixel.properties", "paramixel.properties");
        final TagFilter tagFilter = TagFilterFactory.fromConfigurationParametersAndProperties(
                request.getConfigurationParameters(), rawProperties);

        // Step 1: Discover all test classes (delegated to ClasspathScanner)
        final ClasspathScanner scanner = new ClasspathScanner();
        final Set<Class<?>> testClasses = scanner.discoverTestClasses(request, scanner.buildClassFilter(request));
        LOGGER.fine("Discovered " + testClasses.size() + " potential test classes");

        // Step 2: Validate all discovered classes first (fail-fast on first error)
        final TestValidator validator = new TestValidator();
        final Set<Class<?>> validClasses = new LinkedHashSet<>();
        for (Class<?> testClass : testClasses) {
            final List<ValidationFailure> failures = validator.validateTestClass(testClass);
            if (failures.isEmpty()) {
                validClasses.add(testClass);
            } else {
                // Fail-fast: report first error immediately
                final ValidationFailure firstFailure = failures.get(0);
                LOGGER.warning("Validation failed for test class: " + testClass.getName());
                LOGGER.warning("  - " + firstFailure.getMessage());
                throw new IllegalStateException(firstFailure.getMessage());
            }
        }

        LOGGER.fine("Validated " + validClasses.size() + " test classes");

        // Step 3: Apply tag filtering after validation
        if (tagFilter.hasIncludePatterns()) {
            LOGGER.fine("Applying tag filter - include patterns configured");
        }

        // Step 4: Build descriptors (delegated to DescriptorBuilder)
        final DescriptorBuilder builder = new DescriptorBuilder();
        validClasses.stream()
                .filter(tagFilter::matches)
                .sorted(Comparator.comparing((Class<?> clazz) -> getDisplayName(clazz, clazz.getName())))
                .forEach(testClass ->
                        builder.buildTestClassDescriptor(testClass, engineDescriptor, DISCOVERY_ENGINE_CONTEXT));

        LOGGER.fine(
                "Discovery complete. Found " + engineDescriptor.getChildren().size() + " test classes");
    }

    /**
     * Gets the display name for a test class.
     *
     * @param testClass the test class
     * @param defaultName the default name
     * @return the display name
     */
    private String getDisplayName(final @NonNull Class<?> testClass, final @NonNull String defaultName) {
        final org.paramixel.api.Paramixel.DisplayName displayName =
                testClass.getAnnotation(org.paramixel.api.Paramixel.DisplayName.class);
        return displayName != null ? displayName.value() : defaultName;
    }
}
