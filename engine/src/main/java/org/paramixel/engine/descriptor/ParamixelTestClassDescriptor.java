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

package org.paramixel.engine.descriptor;

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
 *   └── ParamixelTestClassDescriptor (class:com.example.MyTests)
 *         └── ParamixelTestMethodDescriptor (method:testSomething)
 *               └── ParamixelInvocationDescriptor (invocation:0)
 * </pre>
 *
 * @see ParamixelTestMethodDescriptor
 * @see ParamixelInvocationDescriptor
 */
public class ParamixelTestClassDescriptor extends AbstractParamixelDescriptor {

    /**
     * Test class represented by this descriptor.
     */
    private final Class<?> testClass;

    /**
     * Creates a new test class descriptor.
     *
     * @param uniqueId the unique identifier for this descriptor
     * @param testClass the test class this descriptor represents
     * @param displayName the display name for the test class
     */
    public ParamixelTestClassDescriptor(
            final @NonNull UniqueId uniqueId, final @NonNull Class<?> testClass, final @NonNull String displayName) {
        super(uniqueId, displayName, Type.CONTAINER);
        this.testClass = testClass;
    }

    /**
     * Returns the test class this descriptor represents.
     *
     * @return the test class
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
