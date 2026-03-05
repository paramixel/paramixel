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

package org.paramixel.engine.descriptor;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;

/**
 * Test descriptor for an Paramixel test class.
 *
 * <p>This descriptor represents a container for test methods and represents
 * a test class annotated with {@code @Paramixel.TestClass}.
 * It is the parent of {@link ParamixelTestMethodDescriptor} objects.</p>
 *
 * <p>Descriptor hierarchy:
 * <pre>
 * Engine
 *   └── ParamixelTestClassDescriptor (class:MyTests)
 *         └── ParamixelTestMethodDescriptor (method:testSomething)
 *               └── ParamixelInvocationDescriptor (invocation:0)
 * </pre>
 *
 * @see ParamixelTestMethodDescriptor
 * @see ParamixelInvocationDescriptor
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public class ParamixelTestClassDescriptor extends AbstractParamixelDescriptor {

    /**
     * Test class represented by this descriptor.
     */
    private final Class<?> testClass;

    /**
     * Parallelism for argument and invocation execution for this class.
     *
     * <p>Configured by {@code ArgumentsCollector#setParallelism(int)} when
     * the argument supplier uses the context-driven pattern.
     */
    private int argumentParallelism = Math.max(1, Runtime.getRuntime().availableProcessors());

    /**
     * Creates a new instance.
     *
     * @param uniqueId the uniqueId
     * @param testClass the testClass
     * @param displayName the displayName
     * @since 0.0.1
     */
    public ParamixelTestClassDescriptor(
            final @NonNull UniqueId uniqueId, final @NonNull Class<?> testClass, final @NonNull String displayName) {
        super(
                Objects.requireNonNull(uniqueId, "uniqueId must not be null"),
                Objects.requireNonNull(displayName, "displayName must not be null"),
                Type.CONTAINER);
        this.testClass = Objects.requireNonNull(testClass, "testClass must not be null");
    }

    /**
     * Returns the configured per-class parallelism for arguments and invocations.
     *
     * <p>The engine uses this value to control how many argument buckets and method invocations
     * may execute concurrently for this test class.
     *
     * @return the per-class parallelism; always {@code >= 1}
     * @since 0.0.1
     */
    public int getArgumentParallelism() {
        return argumentParallelism;
    }

    /**
     * Sets the per-class parallelism for arguments and invocations.
     *
     * <p>Discovery typically sets this value based on
     * {@code ArgumentsCollector#setParallelism(int)}.
     *
     * @param argumentParallelism the parallelism value; must be {@code >= 1}
     * @throws IllegalArgumentException if {@code argumentParallelism < 1}
     * @since 0.0.1
     */
    public void setArgumentParallelism(final int argumentParallelism) {
        if (argumentParallelism < 1) {
            throw new IllegalArgumentException("argumentParallelism must be >= 1");
        }
        this.argumentParallelism = argumentParallelism;
    }

    /**
     * Returns the test class this descriptor represents.
     *
     * @return the test class
     * @since 0.0.1
     */
    public Class<?> getTestClass() {
        return testClass;
    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }

    @Override
    public Optional<TestSource> getSource() {
        return Optional.of(ClassSource.from(testClass));
    }
}
