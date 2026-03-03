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

import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;

/**
 * Test descriptor for a set of arguments from an argument supplier.
 *
 * <p>This descriptor represents a single set of arguments provided by the
 * {@code @Paramixel.ArgumentsCollector} annotation. When an argument supplier is present,
 * there will be multiple argument descriptors, one for each set of arguments.</p>
 *
 * <p>Descriptor hierarchy:
 * <pre>
 * Engine
 *   └── ParamixelTestClassDescriptor
 *         └── ParamixelTestArgumentDescriptor (argument:0)
 *               └── ParamixelTestMethodDescriptor (method:testMethod1)
 *               └── ParamixelTestMethodDescriptor (method:testMethod2)
 *         └── ParamixelTestArgumentDescriptor (argument:1)
 *               └── ParamixelTestMethodDescriptor (method:testMethod1)
 *               └── ParamixelTestMethodDescriptor (method:testMethod2)
 * </pre>
 *
 * <p>Each argument descriptor has:
 * <ul>
 *   <li>A unique index (0, 1, 2, ...)</li>
 *   <li>A specific argument (from the argument supplier)</li>
 * </ul>
 *
 * @see ParamixelTestClassDescriptor
 * @see ParamixelTestMethodDescriptor
 */
public class ParamixelTestArgumentDescriptor extends AbstractParamixelDescriptor {

    /**
     * Zero-based index of this argument in the supplier output.
     */
    private final int argumentIndex;

    /**
     * Argument value associated with this descriptor.
     */
    private final Object argument;

    /**
     * Creates a new argument descriptor.
     *
     * @param uniqueId the unique identifier for this descriptor
     * @param argumentIndex the zero-based index of this argument set
     * @param argument the argument for this descriptor (may be null)
     * @param displayName the display name for this descriptor
     */
    public ParamixelTestArgumentDescriptor(
            final @NonNull UniqueId uniqueId,
            final int argumentIndex,
            final Object argument,
            final @NonNull String displayName) {
        super(uniqueId, displayName, Type.CONTAINER);
        this.argumentIndex = argumentIndex;
        this.argument = argument;
    }

    /**
     * Returns the zero-based index of this argument.
     *
     * <p>The index corresponds to the position in the argument array
     * provided by the argument supplier. For test classes without an
     * argument supplier, this is always 0.</p>
     *
     * @return the argument index
     */
    public int getArgumentIndex() {
        return argumentIndex;
    }

    /**
     * Returns the argument for this descriptor.
     *
     * <p>When an argument supplier is present, this returns the specific
     * argument for this descriptor. When no supplier is present, this returns null.</p>
     *
     * @return the argument, or null
     */
    public Object getArgument() {
        return argument;
    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }

    @Override
    public String toString() {
        return "ParamixelTestArgumentDescriptor{" + "argumentIndex="
                + argumentIndex + ", argument="
                + argument + ", uniqueId="
                + getUniqueId() + '}';
    }

    @Override
    public Optional<TestSource> getSource() {
        return Optional.empty();
    }
}
