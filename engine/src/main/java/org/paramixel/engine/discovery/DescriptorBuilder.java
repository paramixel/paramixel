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

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteArgumentsCollector;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.ParamixelTestArgumentDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestMethodDescriptor;
import org.paramixel.engine.invoker.ParamixelReflectionInvoker;
import org.paramixel.engine.util.LifecycleMethodUtil;

/**
 * Builds descriptor hierarchy for Paramixel test classes.
 *
 * <p>This class constructs the descriptor tree structure:
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
public final class DescriptorBuilder {

    /**
     * Logger for descriptor building events.
     */
    private static final Logger LOGGER = Logger.getLogger(DescriptorBuilder.class.getName());

    /**
     * Unique ID segment name for class descriptors.
     */
    private static final String CLASS_SEGMENT = "class";

    /**
     * Unique ID segment name for argument descriptors.
     */
    private static final String ARGUMENT_SEGMENT = "argument";

    /**
     * Unique ID segment name for method descriptors.
     */
    private static final String METHOD_SEGMENT = "method";

    /**
     * Creates a new builder instance.
     */
    public DescriptorBuilder() {
        // INTENTIONALLY EMPTY - stateless utility
    }

    /**
     * Builds a test class descriptor and adds it to the engine descriptor.
     *
     * @param testClass the test class
     * @param engineDescriptor the parent engine descriptor
     * @param discoveryContext the discovery context for argument collection
     */
    public void buildTestClassDescriptor(
            final @NonNull Class<?> testClass,
            final @NonNull TestDescriptor engineDescriptor,
            final @NonNull ConcreteEngineContext discoveryContext) {
        LOGGER.fine("Building descriptor for test class: " + testClass.getName());

        final UniqueId classUid = engineDescriptor.getUniqueId().append(CLASS_SEGMENT, testClass.getName());
        final String displayName = getDisplayName(testClass, testClass.getName());
        final ParamixelTestClassDescriptor classDescriptor =
                new ParamixelTestClassDescriptor(classUid, testClass, displayName);

        // Discover and add arguments collector if present
        final ArgumentsCollector argumentsCollector = getArgumentsCollector(testClass, discoveryContext);
        if (argumentsCollector != null) {
            collectArguments(argumentsCollector, classDescriptor);
        } else {
            // No arguments collector - create default argument descriptor
            final UniqueId argUid = classDescriptor.getUniqueId().append(ARGUMENT_SEGMENT, "0");
            final ParamixelTestArgumentDescriptor argDescriptor =
                    new ParamixelTestArgumentDescriptor(argUid, 0, null, "default");
            classDescriptor.addChild(argDescriptor);

            // Discover test methods for default argument
            discoverTestMethods(testClass, argDescriptor);
        }

        engineDescriptor.addChild(classDescriptor);
        LOGGER.fine("Built descriptor for: " + testClass.getName());
    }

    /**
     * Gets the display name for a test class.
     *
     * @param testClass the test class
     * @param defaultName the default name
     * @return the display name
     */
    private String getDisplayName(final @NonNull Class<?> testClass, final @NonNull String defaultName) {
        final Paramixel.DisplayName displayName = testClass.getAnnotation(Paramixel.DisplayName.class);
        return displayName != null ? displayName.value() : defaultName;
    }

    /**
     * Gets the arguments collector from a test class.
     *
     * @param testClass the test class
     * @param discoveryContext the discovery context
     * @return the arguments collector or null if not present
     */
    private @Nullable ArgumentsCollector getArgumentsCollector(
            final @NonNull Class<?> testClass, final @NonNull ConcreteEngineContext discoveryContext) {
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Paramixel.ArgumentsCollector.class)) {
                try {
                    final ConcreteArgumentsCollector collector = new ConcreteArgumentsCollector(discoveryContext);
                    ParamixelReflectionInvoker.invokeStatic(method, collector);
                    return collector;
                } catch (Throwable t) {
                    LOGGER.warning("Failed to invoke arguments collector: " + t.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Collects arguments from the arguments collector.
     *
     * @param collector the arguments collector
     * @param classDescriptor the class descriptor
     */
    private void collectArguments(
            final @NonNull ArgumentsCollector collector, final @NonNull ParamixelTestClassDescriptor classDescriptor) {
        final ConcreteArgumentsCollector concreteCollector = (ConcreteArgumentsCollector) collector;
        final Object[] arguments = concreteCollector.toArray();
        classDescriptor.setArgumentParallelism(concreteCollector.getParallelism());

        for (int i = 0; i < arguments.length; i++) {
            final Object argument = arguments[i];
            final String argumentName = argument instanceof org.paramixel.api.Named
                    ? ((org.paramixel.api.Named) argument).getName()
                    : "argument:" + i;
            final UniqueId argUid = classDescriptor.getUniqueId().append(ARGUMENT_SEGMENT, String.valueOf(i));

            final ParamixelTestArgumentDescriptor argDescriptor =
                    new ParamixelTestArgumentDescriptor(argUid, i, argument, argumentName);
            classDescriptor.addChild(argDescriptor);

            // Discover test methods for this argument
            discoverTestMethods(classDescriptor.getTestClass(), argDescriptor);

            LOGGER.fine("Added argument " + i + ": " + argumentName);
        }
    }

    /**
     * Discovers test methods for an argument.
     *
     * @param testClass the test class
     * @param argumentDescriptor the argument descriptor
     */
    private void discoverTestMethods(
            final @NonNull Class<?> testClass, final @NonNull ParamixelTestArgumentDescriptor argumentDescriptor) {
        final List<Method> testMethods = LifecycleMethodUtil.getTestMethods(testClass);

        for (Method testMethod : testMethods) {
            final UniqueId methodUid = argumentDescriptor.getUniqueId().append(METHOD_SEGMENT, testMethod.getName());
            final String displayName = getDisplayName(testMethod, testMethod.getName());
            final ParamixelTestMethodDescriptor methodDescriptor =
                    new ParamixelTestMethodDescriptor(methodUid, testMethod, displayName);

            argumentDescriptor.addChild(methodDescriptor);
            LOGGER.fine("Added test method: " + testMethod.getName());
        }
    }

    /**
     * Gets the display name for a test method.
     *
     * @param method the method
     * @param defaultName the default name
     * @return the display name
     */
    private String getDisplayName(final @NonNull Method method, final @NonNull String defaultName) {
        final Paramixel.DisplayName displayName = method.getAnnotation(Paramixel.DisplayName.class);
        return displayName != null ? displayName.value() : defaultName;
    }
}
