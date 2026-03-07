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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.logging.Level;
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
import org.paramixel.engine.configuration.EngineConfiguration;
import org.paramixel.engine.configuration.EngineConfigurationResolver;
import org.paramixel.engine.descriptor.AbstractParamixelDescriptor;
import org.paramixel.engine.descriptor.ParamixelEngineDescriptor;
import org.paramixel.engine.descriptor.ParamixelTestClassDescriptor;
import org.paramixel.engine.discovery.ParamixelDiscovery;
import org.paramixel.engine.execution.ParamixelClassRunner;
import org.paramixel.engine.execution.ParamixelConcurrencyLimiter;
import org.paramixel.engine.execution.ParamixelExecutionRuntime;
import org.paramixel.engine.listener.ParamixelEngineExecutionListener;
import org.paramixel.engine.util.FastIdUtil;
import org.paramixel.engine.util.PropertiesLoaderUtil;

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
     * Properties file name used for project-root configuration.
     */
    private static final String PROPERTIES_FILE_NAME = "paramixel.properties";

    /**
     * Configuration key used for reporting properties I/O errors.
     */
    private static final String PROPERTIES_FILE_KEY = "paramixel.properties";

    /**
     * Creates a new test engine instance.
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
        EngineConfiguration engineConfiguration = null;
        Exception exception = null;

        try {
            final Properties rawProperties =
                    PropertiesLoaderUtil.loadProjectRootPropertiesOrFail(PROPERTIES_FILE_NAME, PROPERTIES_FILE_KEY);
            final Properties normalizedProperties = PropertiesLoaderUtil.normalizeAllValues(rawProperties);
            engineConfiguration = EngineConfigurationResolver.resolveForExecution(
                    executionRequest.getConfigurationParameters(), normalizedProperties, DEFAULT_PARALLELISM);
        } catch (Exception e) {
            exception = e;
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }

        final boolean mavenInvocationMode = engineConfiguration != null && engineConfiguration.mavenInvocationMode();
        final EngineExecutionListener engineExecutionListener;
        if (mavenInvocationMode) {
            engineExecutionListener = new ParamixelEngineExecutionListener(
                    executionRequest.getEngineExecutionListener(), engineConfiguration.summaryClassNameMaxLength());
        } else {
            engineExecutionListener = executionRequest.getEngineExecutionListener();
        }

        engineExecutionListener.executionStarted(executionRequest.getRootTestDescriptor());

        if (exception != null) {
            engineExecutionListener.executionFinished(
                    executionRequest.getRootTestDescriptor(), TestExecutionResult.failed(exception));
            if (mavenInvocationMode) {
                LOGGER.info("TESTS FAILED");
            }
            return;
        }

        final Map<Class<?>, ClassContext> classContexts = new HashMap<>();
        final Map<Class<?>, Object> testInstances = new HashMap<>();

        try {
            final Properties properties = engineConfiguration.normalizedProperties();

            if (mavenInvocationMode) {
                LOGGER.info("Paramixel Engine invoked by Maven");
                LOGGER.info("Paramixel Engine Configuration:");
                properties.stringPropertyNames().stream().sorted().forEach(k -> {
                    LOGGER.info("  " + k + " = " + properties.getProperty(k));
                });
            }

            final ConcreteEngineContext engineContext =
                    new ConcreteEngineContext(ENGINE_ID, properties, engineConfiguration.parallelism());

            final List<ParamixelTestClassDescriptor> classDescriptors =
                    executionRequest.getRootTestDescriptor().getChildren().stream()
                            .filter(d -> d instanceof ParamixelTestClassDescriptor)
                            .map(d -> (ParamixelTestClassDescriptor) d)
                            .sorted(Comparator.comparing(AbstractParamixelDescriptor::getDisplayName))
                            .toList();

            try (ParamixelExecutionRuntime runtime = new ParamixelExecutionRuntime(engineConfiguration.parallelism())) {
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
