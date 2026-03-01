/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

package org.paramixel.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.AbstractParamixelDescriptor;
import org.paramixel.engine.descriptor.ParamixelEngineDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.discovery.ParamixelDiscovery;
import org.paramixel.engine.execution.ParamixelClassRunner;
import org.paramixel.engine.execution.ParamixelConcurrencyLimiter;
import org.paramixel.engine.execution.ParamixelExecutionRuntime;
import org.paramixel.engine.listener.ParamixelEngineExecutionListener;
import org.paramixel.engine.util.FastId;

/**
 * Implementation of the JUnit Platform {@link TestEngine} for the Paramixel test framework.
 *
 * <p>This engine discovers and executes tests annotated with {@code @Paramixel.TestClass}
 * annotations. It provides:</p>
 * <ul>
 *   <li>Test discovery with hierarchical descriptors</li>
 *   <li>Virtual-thread execution with a hard global concurrency cap</li>
 *   <li>Semaphore-based concurrency limiting (classes + extra parallel work)</li>
 *   <li>Full lifecycle method execution with pairing guarantees</li>
 * </ul>
 *
 * <p>Engine ID: {@code paramixel}</p>
 *
 * @see Paramixel.TestClass
 * @see ParamixelDiscovery
 * @see ParamixelExecutionRuntime
 */
public class ParamixelTestEngine implements TestEngine {

    /**
     * The unique identifier for this engine.
     */
    public static final String ENGINE_ID = "paramixel";

    /**
     * Default parallelism for test class execution.
     *
     * <p>This value is used when no explicit {@code parallelism} configuration
     * parameter is provided to the engine.</p>
     */
    private static final int DEFAULT_CLASS_PARALLELISM =
            Math.max(1, Runtime.getRuntime().availableProcessors());

    @Override
    public @NonNull String getId() {
        return ENGINE_ID;
    }

    @Override
    public @NonNull TestDescriptor discover(
            final @NonNull EngineDiscoveryRequest engineDiscoveryRequest, final @NonNull UniqueId uniqueId) {

        final TestDescriptor engineDescriptor = new ParamixelEngineDescriptor(uniqueId, ENGINE_ID);

        new ParamixelDiscovery().discoverTests(engineDiscoveryRequest, engineDescriptor);

        return engineDescriptor;
    }

    @Override
    public void execute(final @NonNull ExecutionRequest executionRequest) {
        // Backward compatible: historically this was "paramixel.invokedBy".
        final boolean invokedByMaven = executionRequest
                .getConfigurationParameters()
                .get("invokedBy")
                .or(() -> executionRequest.getConfigurationParameters().get("invokedBy"))
                .map("maven"::equals)
                .orElse(false);

        final int classParallelism = executionRequest
                .getConfigurationParameters()
                .get("parallelism")
                .map(Integer::parseInt)
                .orElse(DEFAULT_CLASS_PARALLELISM);

        final EngineExecutionListener engineExecutionListener =
                invokedByMaven ? new ParamixelEngineExecutionListener() : executionRequest.getEngineExecutionListener();

        engineExecutionListener.executionStarted(executionRequest.getRootTestDescriptor());

        Exception exception = null;

        try {
            final Properties properties = new Properties();

            final File propertiesFile = new File("paramixel.properties");
            if (propertiesFile.isFile()) {
                try (InputStream in = new FileInputStream(propertiesFile)) {
                    properties.load(in);
                }
            }

            properties.setProperty("invokedBy", invokedByMaven ? "maven" : "junit");
            properties.setProperty("parallelism", String.valueOf(classParallelism));

            System.out.println("Paramixel Engine Configuration:");

            properties.stringPropertyNames().stream()
                    .sorted()
                    .forEach(k -> System.out.println("  " + k + " = " + properties.getProperty(k)));

            final ConcreteEngineContext engineContext =
                    new ConcreteEngineContext(ENGINE_ID, properties, classParallelism);

            final Map<Class<?>, ClassContext> classContexts = new HashMap<>();
            final Map<Class<?>, Object> testInstances = new HashMap<>();

            final List<ParamixelTestClassDescriptor> classDescriptors =
                    executionRequest.getRootTestDescriptor().getChildren().stream()
                            .filter(d -> d instanceof ParamixelTestClassDescriptor)
                            .map(d -> (ParamixelTestClassDescriptor) d)
                            .sorted(Comparator.comparing(AbstractParamixelDescriptor::getDisplayName))
                            .toList();

            try (ParamixelExecutionRuntime runtime = ParamixelExecutionRuntime.createDefault()) {
                final ParamixelClassRunner classRunner = new ParamixelClassRunner(
                        runtime, engineContext, engineExecutionListener, classContexts, testInstances);

                final List<Future<?>> futures = new ArrayList<>();
                for (ParamixelTestClassDescriptor classDescriptor : classDescriptors) {
                    final ParamixelConcurrencyLimiter.ClassPermit permit =
                            runtime.limiter().acquireClassExecution();
                    futures.add(runtime.submitNamed(FastId.getId(6), () -> {
                        try (permit) {
                            classRunner.runTestClass(classDescriptor);
                        }
                    }));
                }

                for (Future<?> future : futures) {
                    future.get();
                }
            }

        } catch (Exception e) {
            exception = e;
        }

        if (exception != null) {
            engineExecutionListener.executionFinished(
                    executionRequest.getRootTestDescriptor(), TestExecutionResult.failed(exception));
        } else {
            engineExecutionListener.executionFinished(
                    executionRequest.getRootTestDescriptor(), TestExecutionResult.successful());
        }
    }
}
