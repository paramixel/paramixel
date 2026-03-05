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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.MethodSource;

/**
 * Test descriptor for an Paramixel test method.
 *
 * <p>This descriptor represents a single test method annotated with {@code @Paramixel.Test}.
 * It is a child of {@link ParamixelTestArgumentDescriptor} and represents one execution
 * with a specific set of arguments.</p>
 *
 * <p>Descriptor hierarchy:
 * <pre>
 * Engine
 *   └── ParamixelTestClassDescriptor
 *         └── ParamixelTestArgumentDescriptor
 *               └── ParamixelTestMethodDescriptor (method:testSomething)
 *         └── ParamixelTestArgumentDescriptor
 *               └── ParamixelTestMethodDescriptor (method:testSomething)
 * </pre>
 *
 * @see ParamixelTestClassDescriptor
 * @see ParamixelTestArgumentDescriptor
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public class ParamixelTestMethodDescriptor extends AbstractParamixelDescriptor {

    /**
     * Reflected test method represented by this descriptor.
     */
    private final Method testMethod;

    /**
     * Creates a new instance.
     *
     * @param uniqueId the uniqueId
     * @param testMethod the testMethod
     * @param displayName the displayName
     * @since 0.0.1
     */
    public ParamixelTestMethodDescriptor(
            final @NonNull UniqueId uniqueId, final @NonNull Method testMethod, final @NonNull String displayName) {
        super(uniqueId, displayName, Type.TEST);
        this.testMethod = testMethod;
    }

    /**
     * Returns the test method this descriptor represents.
     *
     * @return the test method
     * @since 0.0.1
     */
    public Method getTestMethod() {
        return testMethod;
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    @Override
    public Set<TestDescriptor> getChildren() {
        return Collections.emptySet();
    }

    @Override
    public Optional<TestSource> getSource() {
        return Optional.of(MethodSource.from(testMethod));
    }
}
