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
import java.util.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.paramixel.api.ClassContext;
import org.paramixel.engine.api.ConcreteClassContext;
import org.paramixel.engine.api.ConcreteEngineContext;
import org.paramixel.engine.descriptor.AbstractParamixelDescriptor;
import org.paramixel.engine.descriptor.ParamixelEngineDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.discovery.ParamixelDiscovery;
import org.paramixel.engine.execution.ParamixelClassRunner;
import org.paramixel.engine.execution.ParamixelConcurrencyLimiter;
import org.paramixel.engine.execution.ParamixelExecutionRuntime;
import org.paramixel.engine.listener.ParamixelEngineExecutionListener;
import org.paramixel.engine.util.FastIdUtil;

/**
 * Provides the Paramixel JUnit Platform {@link TestEngine} implementation.
 *
 * <p>This engine discovers tests annotated with {@link org.paramixel.api.Paramixel.TestClass} and executes them
 * through the Paramixel engine runtime. It builds a descriptor tree during
 * {@link #discover(EngineDiscoveryRequest, UniqueId)} and schedules class-level execution during
 * {@link #execute(ExecutionRequest)}.
 *
 * <p><b>Execution model</b>
 * <ul>
 *   <li>Uses virtual threads for concurrency.
 *   <li>Applies a global concurrency cap via {@link ParamixelConcurrencyLimiter}.
 *   <li>Delegates test-class execution to {@link ParamixelClassRunner}.
 * </ul>
 *
 * <p><b>Thread safety</b>
 * <p>This class is stateless and thread-safe. It creates per-execution state inside
 * {@link #execute(ExecutionRequest)}.
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class ParamixelTestEngine implements TestEngine {

    /**
     * Logger for engine-level diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(ParamixelTestEngine.class.getName());

    /**
     * The unique identifier for this engine.
     */
    public static final String ENGINE_ID = "paramixel";

    /**
     * Default class-level parallelism when not configured.
     *
     * <p>The value is always {@code >= 1}.
     */
    private static final int DEFAULT_PARALLELISM =
            Math.max(1, Runtime.getRuntime().availableProcessors());

    /**
     * Creates a new test engine instance.
     *
     * @since 0.0.1
     */
    public ParamixelTestEngine() {
        // INTENTIONALLY EMPTY
    }

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
        final boolean invokedByMaven = executionRequest
                .getConfigurationParameters()
                .get("invokedBy")
                .or(() -> executionRequest.getConfigurationParameters().get("invokedBy"))
                .map("maven"::equals)
                .orElse(false);

        // Load base properties from file
        final Properties properties = new Properties();
        final File propertiesFile = new File("paramixel.properties");
        if (propertiesFile.isFile()) {
            try (InputStream in = new FileInputStream(propertiesFile)) {
                properties.load(in);
            } catch (Exception e) {
                LOGGER.warning("Failed to load paramixel.properties: " + e.getMessage());
            }
        }

        // Determine parallelism: config params override properties file
        final int maxParallelism = executionRequest
                .getConfigurationParameters()
                .get("paramixel.parallelism")
                .map(Integer::parseInt)
                .orElseGet(() -> {
                    String value = properties.getProperty("paramixel.parallelism");
                    if (value != null && !value.trim().isEmpty()) {
                        try {
                            return Integer.parseInt(value.trim());
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid paramixel.parallelism value in properties file: " + value);
                        }
                    }
                    return DEFAULT_PARALLELISM;
                });

        final EngineExecutionListener engineExecutionListener = invokedByMaven
                ? new ParamixelEngineExecutionListener(executionRequest.getEngineExecutionListener())
                : executionRequest.getEngineExecutionListener();

        engineExecutionListener.executionStarted(executionRequest.getRootTestDescriptor());

        Exception exception = null;

        final Map<Class<?>, ClassContext> classContexts = new HashMap<>();
        final Map<Class<?>, Object> testInstances = new HashMap<>();

        try {
            properties.setProperty("invokedBy", invokedByMaven ? "maven" : "junit");
            properties.setProperty("paramixel.parallelism", String.valueOf(maxParallelism));

            if (invokedByMaven) {
                LOGGER.info("Paramixel Engine invoked by Maven");
                LOGGER.info("Paramixel Engine Configuration:");
                properties.stringPropertyNames().stream().sorted().forEach(k -> {
                    LOGGER.info("  " + k + " = " + properties.getProperty(k));
                });
            }

            final ConcreteEngineContext engineContext =
                    new ConcreteEngineContext(ENGINE_ID, properties, maxParallelism);

            final List<ParamixelTestClassDescriptor> classDescriptors =
                    executionRequest.getRootTestDescriptor().getChildren().stream()
                            .filter(d -> d instanceof ParamixelTestClassDescriptor)
                            .map(d -> (ParamixelTestClassDescriptor) d)
                            .sorted(Comparator.comparing(AbstractParamixelDescriptor::getDisplayName))
                            .toList();

            try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(maxParallelism)) {
                final ParamixelClassRunner classRunner = new ParamixelClassRunner(
                        runtime, engineContext, engineExecutionListener, classContexts, testInstances);

                final List<Future<?>> futures = new ArrayList<>();
                for (ParamixelTestClassDescriptor classDescriptor : classDescriptors) {
                    final ParamixelConcurrencyLimiter.ClassPermit permit =
                            runtime.limiter().acquireClassExecution();
                    futures.add(runtime.submitNamed(FastIdUtil.getId(6), () -> {
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

        Throwable firstTestFailure = null;
        for (ClassContext classContext : classContexts.values()) {
            if (classContext instanceof ConcreteClassContext) {
                final Throwable failure = ((ConcreteClassContext) classContext).getFirstFailure();
                if (failure != null) {
                    firstTestFailure = failure;
                    break;
                }
            }
        }

        final TestExecutionResult rootResult;
        if (exception != null) {
            rootResult = TestExecutionResult.failed(exception);
        } else if (firstTestFailure != null) {
            rootResult = TestExecutionResult.failed(firstTestFailure);
        } else {
            rootResult = TestExecutionResult.successful();
        }

        engineExecutionListener.executionFinished(executionRequest.getRootTestDescriptor(), rootResult);
    }
}
