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

import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;

/**
 * Test descriptor for a single test method invocation.
 *
 * <p>This descriptor represents a single execution of a test method with a specific
 * set of arguments. When an argument supplier is present, there will be multiple
 * invocation descriptors, one for each set of arguments.</p>
 *
 * <p>Descriptor hierarchy:
 * <pre>
 * Engine
 *   └── ParamixelTestClassDescriptor
 *         └── ParamixelTestMethodDescriptor
 *               └── ParamixelInvocationDescriptor (invocation:0)
 *               └── ParamixelInvocationDescriptor (invocation:1)
 * </pre>
 *
 * <p>Each invocation has:
 * <ul>
 *   <li>A unique index (0, 1, 2, ...)</li>
 *   <li>A specific argument (from the argument supplier)</li>
 * </ul>
 *
 * @see ParamixelTestClassDescriptor
 * @see ParamixelTestMethodDescriptor
 */
public class ParamixelInvocationDescriptor extends AbstractParamixelDescriptor {

    /**
     * Zero-based index of this invocation.
     */
    private final int invocationIndex;

    /**
     * Argument value bound to this invocation.
     */
    private final Object argument;

    /**
     * Creates a new invocation descriptor.
     *
     * @param uniqueId the unique identifier for this descriptor
     * @param invocationIndex the zero-based index of this invocation
     * @param argument the argument for this invocation (may be null)
     */
    public ParamixelInvocationDescriptor(
            final @NonNull UniqueId uniqueId, final @NonNull int invocationIndex, final @NonNull Object argument) {
        super(uniqueId, "invocation:" + invocationIndex, Type.TEST);
        this.invocationIndex = invocationIndex;
        this.argument = argument;
    }

    /**
     * Returns the argument for this invocation.
     *
     * <p>When an argument supplier is present, this returns the specific
     * argument for this invocation. When no supplier is present, or for
     * lifecycle methods, this returns null.</p>
     *
     * @return the invocation argument, or null
     */
    public Object getArgument() {
        return argument;
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
    public String toString() {
        return "ParamixelInvocationDescriptor{" + "invocationIndex="
                + invocationIndex + ", argument="
                + argument + ", uniqueId="
                + getUniqueId() + '}';
    }
}
